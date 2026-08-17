package com.ulticode.modules.submission.reaper;

import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.fence.LeaseConstants;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Recovers JUDGING submissions whose lease has lapsed (ADR-003 M3b, F7).
 *
 * <p>The Redis enqueue is deferred to {@code afterCommit} so the
 * non-transactional side-effect never races the DB commit.
 *
 * <p>Each expired submission is recovered in its own
 * {@code REQUIRES_NEW} transaction: a single persistently failing row (e.g.
 * an outbox insert exceeding a column width) rolls back only itself and is
 * retried on the next sweep, instead of wedging the whole recovery batch
 * every cycle.
 *
 * <p><b>SPLIT-004 AC4 retirement note (cutover state):</b> with the runtime
 * cutover active ({@code app.submission.routing.mode=remote}), the regular
 * path reaps leases in
 * the Submission owner schema (backend-submission owns it). This reaper
 * remains active only for App-local compatibility/rollback when the routing
 * flags are reverted. Kept as a clearly labeled
 * compatibility component; do not extend it with new regular-path behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.use-generation-fence",
        havingValue = "true")
public class JudgingLeaseReaper {

    private final SubmissionMapper submissionMapper;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final JudgeEnqueuePort judgeEnqueuePort;
    private final FeatureFlagsProperties featureFlags;
    private final UuidGenerator uuidGenerator;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    private volatile TransactionTemplate rowTransactionTemplate;

    @Scheduled(fixedDelayString = "${judge.reaper.interval-ms:5000}",
            initialDelayString = "${judge.reaper.initial-delay-ms:10000}")
    public int recoverExpiredLeases() {
        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(
                LeaseConstants.REAPER_BATCH_SIZE);
        if (expired.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        List<String> failed = new ArrayList<>();
        for (Submission s : expired) {
            try {
                if (recoverOne(s)) {
                    recovered++;
                    incrementLeaseExpired();
                }
            } catch (Exception e) {
                failed.add(s.getId());
                log.error("Lease recovery failed for submission {} (row kept JUDGING; "
                        + "next sweep retries): {}", s.getId(), e.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            log.error("Lease recovery failed for {} of {} expired submissions: {}",
                    failed.size(), expired.size(), failed);
        }
        return recovered;
    }

    /** Run one recovery in its own REQUIRES_NEW transaction when a transaction manager is available. */
    private boolean recoverOne(Submission s) {
        TransactionTemplate tt = rowTransactionTemplate();
        if (tt == null) {
            return recoverOneInTx(s);
        }
        Boolean ok = tt.execute(status -> recoverOneInTx(s));
        return Boolean.TRUE.equals(ok);
    }

    private boolean recoverOneInTx(Submission s) {
        long observedGen = s.getGeneration() != null ? s.getGeneration() : 1L;
        long newGen = observedGen + 1;
        int rows = submissionMapper.bumpGenerationAndReset(s.getId(), observedGen, newGen);
        if (rows != 1) {
            log.debug("Lease recovery skipped for submission {} (gen {} already moved)",
                    s.getId(), observedGen);
            return false;
        }

        if (featureFlags.isUseJudgeOutbox()) {
            boolean portActive = featureFlags.getJudgeQueue().isUsePort();
            try {
                judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                        s, String.valueOf(s.getProblemId()), newGen, !portActive, uuidGenerator));
                log.info("reaper.outbox.insert submissionId={} problemId={} generation={} is_shadow={} portActive={}",
                        s.getId(), s.getProblemId(), newGen, !portActive, portActive);
            } catch (Exception e) {
                if (portActive) {
                    // Fail loud: without the outbox row the dispatch is lost
                    // (the legacy enqueue adapter is disabled during cutover).
                    // The row transaction rolls back — the submission stays
                    // JUDGING with an expired lease and the next sweep retries.
                    throw new IllegalStateException(
                            "Judge outbox insert failed during Streams cutover for submission "
                                    + s.getId(), e);
                }
                log.debug("Outbox insert skipped for submission {} gen {}: {}",
                        s.getId(), newGen, e.getMessage());
            }
        }

        log.info("Recovered expired lease for submission {} (gen {} -> {})",
                s.getId(), observedGen, newGen);
        enqueueAfterCommit(s);
        return true;
    }

    private void enqueueAfterCommit(Submission s) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueueLegacy(s);
                }
            });
        } else {
            enqueueLegacy(s);
        }
    }

    private void enqueueLegacy(Submission s) {
        try {
            judgeEnqueuePort.enqueueJudgeJob(
                    s.getId(),
                    String.valueOf(s.getProblemId()),
                    s.getUserId(),
                    s.getLanguage(),
                    s.getCode());
        } catch (Exception e) {
            log.warn("Post-commit enqueue failed for submission {} (gen bumped; "
                            + "outbox row recorded for replay): {}",
                    s.getId(), e.getMessage());
        }
    }

    private TransactionTemplate rowTransactionTemplate() {
        TransactionTemplate local = this.rowTransactionTemplate;
        if (local == null) {
            PlatformTransactionManager tm = transactionManagerProvider.getIfAvailable();
            if (tm == null) {
                return null;
            }
            TransactionTemplate tt = new TransactionTemplate(tm);
            tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.rowTransactionTemplate = tt;
            local = tt;
        }
        return local;
    }

    private void incrementLeaseExpired() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.lease.expired").increment();
        }
    }
}

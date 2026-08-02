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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Recovers JUDGING submissions whose lease has lapsed (ADR-003 M3b, F7).
 *
 * <p>The Redis enqueue is deferred to {@code afterCommit} so the
 * non-transactional side-effect never races the DB commit.
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

    @Scheduled(fixedDelayString = "${judge.reaper.interval-ms:5000}",
            initialDelayString = "${judge.reaper.initial-delay-ms:10000}")
    @Transactional
    public int recoverExpiredLeases() {
        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(
                LeaseConstants.REAPER_BATCH_SIZE);
        if (expired.isEmpty()) {
            return 0;
        }

        List<Submission> toEnqueue = new ArrayList<>(expired.size());
        int recovered = 0;
        for (Submission s : expired) {
            long observedGen = s.getGeneration() != null ? s.getGeneration() : 1L;
            long newGen = observedGen + 1;
            int rows = submissionMapper.bumpGenerationAndReset(s.getId(), observedGen, newGen);
            if (rows != 1) {
                log.debug("Lease recovery skipped for submission {} (gen {} already moved)",
                        s.getId(), observedGen);
                continue;
            }

            if (featureFlags.isUseJudgeOutbox() && judgeOutboxMapper != null) {
                boolean portActive = featureFlags.getJudgeQueue().isUsePort();
                try {
                    judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                            s, String.valueOf(s.getProblemId()), newGen, !portActive, uuidGenerator));
                    log.info("reaper.outbox.insert submissionId={} problemId={} generation={} is_shadow={} portActive={}",
                            s.getId(), s.getProblemId(), newGen, !portActive, portActive);
                } catch (Exception e) {
                    log.debug("Outbox insert skipped for submission {} gen {}: {}",
                            s.getId(), newGen, e.getMessage());
                }
            }

            toEnqueue.add(s);
            recovered++;
            incrementLeaseExpired();
            log.info("Recovered expired lease for submission {} (gen {} -> {})",
                    s.getId(), observedGen, newGen);
        }

        if (!toEnqueue.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            final List<Submission> enqueueBatch = toEnqueue;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Submission s : enqueueBatch) {
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
                }
            });
        } else if (!toEnqueue.isEmpty()) {
            for (Submission s : toEnqueue) {
                judgeEnqueuePort.enqueueJudgeJob(
                        s.getId(),
                        String.valueOf(s.getProblemId()),
                        s.getUserId(),
                        s.getLanguage(),
                        s.getCode());
            }
        }
        return recovered;
    }

    private void incrementLeaseExpired() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.lease.expired").increment();
        }
    }
}

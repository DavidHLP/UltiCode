package com.ulticode.modules.submission.reaper;

import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.fence.LeaseConstants;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
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
 * <p>Replaces the legacy 5-minute Pending-only reaper (ADR-000 reject list). The
 * DB part of recovery is a <b>single transaction</b>: {@code SELECT ... FOR
 * UPDATE SKIP LOCKED} locks the expired rows, then for each row we bump the
 * generation and reset to Pending via {@link SubmissionMapper#bumpGenerationAndReset}
 * (CAS on expected generation) and write a shadow outbox row. These DB writes
 * commit atomically so a crash mid-recovery cannot leave the submission in an
 * orphaned "bumped generation + no outbox" state.
 *
 * <p>The Redis enqueue ({@link QueueService#enqueueJudgeJob}) is deferred to an
 * <b>{@code afterCommit}</b> callback (H1 fix). Redis is not transactional with
 * MySQL: enqueueing inside the DB transaction creates two failure windows —
 * (1) commit-then-crash-before-Redis-ack loses the job, and (2) a mid-loop
 * exception rolls back the DB but leaves N already-enqueued Redis jobs
 * orphaned. Moving the enqueue to {@code afterCommit} collapses window (2)
 * entirely (Redis is never touched unless the DB commits) and shrinks (1) to
 * "DB committed, Redis lost", which the shadow comparator / a future outbox
 * replay can still reconcile.
 *
 * <p>Only active when {@code app.features.use-generation-fence=true}; flag-off
 * deployments keep the legacy judging flow with no lease and no reaper.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.use-generation-fence",
        havingValue = "true")
public class JudgingLeaseReaper {

    private final SubmissionMapper submissionMapper;
    private final QueueService queueService;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    /** Nullable so unit tests without a registry still compile/run. */
    private final MeterRegistry meterRegistry;
    private final UuidGenerator uuidGenerator;

    /**
     * Sweep expired JUDGING rows. Runs every 5 seconds with a single-threaded
     * scheduler tick; {@code FOR UPDATE SKIP LOCKED} makes multiple instances
     * safe. The {@code @Transactional} boundary covers the DB part of the batch
     * (SELECT FOR UPDATE + bump + outbox insert) so a mid-sweep crash rolls
     * back every partial bump. Redis enqueue is deferred to
     * {@code afterCommit} (H1 fix) so the non-transactional side-effect never
     * races the DB commit.
     *
     * @return number of rows recovered this sweep (for tests / metrics)
     */
    @Scheduled(fixedDelayString = "${judge.reaper.interval-ms:5000}",
            initialDelayString = "${judge.reaper.initial-delay-ms:10000}")
    @Transactional
    public int recoverExpiredLeases() {
        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(
                LeaseConstants.REAPER_BATCH_SIZE);
        if (expired.isEmpty()) {
            return 0;
        }

        // Collected inside the transaction; enqueued only after commit (H1).
        List<Submission> toEnqueue = new ArrayList<>(expired.size());
        int recovered = 0;
        for (Submission s : expired) {
            long observedGen = s.getGeneration() != null ? s.getGeneration() : 1L;
            long newGen = observedGen + 1;
            int rows = submissionMapper.bumpGenerationAndReset(s.getId(), observedGen, newGen);
            if (rows != 1) {
                // Concurrent modification (another reaper or a rejudge already
                // bumped this generation); skip rather than double-recover.
                log.debug("Lease recovery skipped for submission {} (gen {} already moved)",
                        s.getId(), observedGen);
                continue;
            }

            // Outbox double-write at the new generation (stays in the DB
            // transaction so the bump + outbox commit atomically).
            //
            // P0 #11 fix: previously hard-coded `is_shadow=true`. Under
            // `use-port=true`, the dispatcher ignores shadow rows, so a
            // recovered submission's outbox row was never picked up and the
            // submission was stranded Pending forever. Now `is_shadow =
            // !portActive` so the port cutover writes a real row the
            // dispatcher can enqueue. Shadow mode (use-port=false) preserves
            // the original double-write semantics.
            if (featureFlags.isUseJudgeOutbox() && judgeOutboxMapper != null) {
                boolean portActive = featureFlags.getJudgeQueue().isUsePort();
                try {
                    judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                            s, String.valueOf(s.getProblemId()), newGen, !portActive, uuidGenerator));
                    // Canary observability (P0 #11): log every successful
                    // reaper outbox insert with is_shadow / portActive pair
                    // so post-canary grep (24h) can verify no
                    // `is_shadow=true ∧ portActive=true` slips through.
                    log.info("reaper.outbox.insert submissionId={} problemId={} generation={} is_shadow={} portActive={}",
                            s.getId(), s.getProblemId(), newGen, !portActive, portActive);
                } catch (Exception e) {
                    // The unique key (submission_id, generation) may reject a
                    // duplicate if a concurrent recovery already wrote this gen;
                    // that is benign — the intent is already recorded.
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

        // H1 fix: defer the Redis enqueue to afterCommit so the non-transactional
        // RQueue.add cannot (a) leave an orphaned Redis job when the DB rolls
        // back, or (b) be lost when the JVM crashes between DB commit and Redis
        // ack (the shadow outbox row committed above still records the intent
        // for a future replay). The DB transaction owns the bump + outbox; the
        // Redis leg follows only on a successful commit.
        if (!toEnqueue.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            final List<Submission> enqueueBatch = toEnqueue;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Submission s : enqueueBatch) {
                        // Real re-delivery goes through the legacy RQueue (M3c
                        // will hand this to the outbox dispatcher). Generation
                        // fence guarantees any duplicate judge result from the
                        // old generation is harmless.
                        try {
                            queueService.enqueueJudgeJob(
                                    s.getId(),
                                    String.valueOf(s.getProblemId()),
                                    s.getUserId(),
                                    s.getLanguage(),
                                    s.getCode());
                        } catch (Exception e) {
                            // Redis down post-commit: the DB bump + outbox row
                            // already committed, so the row is recoverable by a
                            // future sweep / outbox replay. Log and continue so
                            // one bad enqueue does not block the rest of the batch.
                            log.warn("Post-commit enqueue failed for submission {} (gen bumped; "
                                    + "shadow outbox row recorded for replay): {}",
                                    s.getId(), e.getMessage());
                        }
                    }
                }
            });
        } else if (!toEnqueue.isEmpty()) {
            // No active transaction synchronization (e.g. a direct unit-test
            // call without a Spring tx manager): enqueue immediately so the
            // behavior is not silently lost.
            for (Submission s : toEnqueue) {
                queueService.enqueueJudgeJob(
                        s.getId(),
                        String.valueOf(s.getProblemId()),
                        s.getUserId(),
                        s.getLanguage(),
                        s.getCode());
            }
        }
        return recovered;
    }

    /**
     * Increment the {@code judge.lease.expired} counter. No-op without a registry.
     */
    private void incrementLeaseExpired() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.lease.expired").increment();
        }
    }
}

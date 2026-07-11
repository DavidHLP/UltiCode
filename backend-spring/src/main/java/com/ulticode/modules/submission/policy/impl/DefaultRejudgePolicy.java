package com.ulticode.modules.submission.policy.impl;

import com.ulticode.common.util.AuditContext;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.fence.SubmissionStateMachine;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.policy.RejudgePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link RejudgePolicy} implementation.
 *
 * <p>Owns the ADR-003 M3b fenced rejudge state machine that used to live
 * as a 165-line private method inside
 * {@code AdminSubmissionServiceImpl}. Branches on observed status:
 * <ul>
 *   <li>Terminal + rejudgeable → bump generation atomically, reset to
 *       Pending, outbox. {@code retry_count} persists via a targeted CAS
 *       — never via {@code updateById(submission)} which would clobber the
 *       fence columns (F1).</li>
 *   <li>JUDGING → force lease expiry + revoke the attempt (F2); the lease
 *       reaper atomically bumps generation in its own transaction.</li>
 *   <li>Already Pending (or unknown) → outbox without a bump.</li>
 * </ul>
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRejudgePolicy implements RejudgePolicy {

    private final SubmissionMapper submissionMapper;
    private final QueueService queueService;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    private final TransactionTemplate transactionTemplate;
    private final UuidGenerator uuidGenerator;

    /**
     * Run the fenced rejudge flow on {@code submission}.
     *
     * @param submission the submission to rejudge
     * @param result     the result DTO to populate
     * @return the same result DTO with success/error populated
     */
    @Override
    public RejudgeResult rejudgeFenced(Submission submission, RejudgeResult result) {
        String id = submission.getId();

        // Tracks whether the DB mutation branch actually won the right to
        // dispatch. The JUDGING branch never dispatches here (the reaper does).
        // A bump-CAS loss means another writer already dispatched; we surface a
        // conflict rather than enqueueing a duplicate the fence would drop.
        final boolean[] dispatchWon = { false };

        try {
            transactionTemplate.executeWithoutResult(status -> {
                SubmissionStatus current = SubmissionStatus.fromDbName(submission.getStatus());
                boolean judging = current == SubmissionStatus.JUDGING;
                boolean rejudgeable = SubmissionStateMachine.canAdminRejudgeFrom(current);

                // Increment retry count regardless of branch (matches legacy).
                // Persisted via targeted bumpRetryCount CAS below — never via
                // updateById(submission), which would clobber the fence columns
                // (F1, C1).
                submission.setRetryCount(
                    submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
                );

                if (judging) {
                    // Force the lease to expire AND revoke the active attempt (F2);
                    // the reaper will bump generation atomically. We do NOT bump
                    // here to avoid racing the worker.
                    if (submission.getCurrentAttemptId() != null) {
                        submissionMapper.forceLeaseExpiry(id, submission.getCurrentAttemptId());
                    }
                    // C1 fix: persist retry_count via a TARGETED update that
                    // touches ONLY retry_count. We must NOT call updateById here.
                    // Status stays Judging until the reaper flips it to Pending.
                    submissionMapper.bumpRetryCount(id, 1);
                    // JUDGING branch never dispatches here — the reaper bumps
                    // the generation and dispatches from its own single
                    // transaction. Outbox write is intentionally SKIPPED.
                } else if (rejudgeable) {
                    // Terminal -> bump generation atomically, reset to Pending.
                    long expectedGen = submission.getGeneration() != null ? submission.getGeneration() : 1L;
                    long newGen = expectedGen + 1;
                    int bumped = submissionMapper.bumpGenerationAndReset(id, expectedGen, newGen);
                    boolean bumpWon = bumped == 1;
                    submissionMapper.bumpRetryCount(id, 1);
                    if (bumpWon) {
                        submission.setGeneration(newGen);
                        // H2 fix: only write the outbox shadow when the
                        // generation bump actually won. A race-lost bump
                        // means the row's real generation already moved; the
                        // winning writer owns the outbox row at the new gen.
                        writeRejudgeOutbox(submission, newGen);
                        dispatchWon[0] = true;
                    } else {
                        // F1: the bump CAS lost a race. Do NOT enqueue —
                        // the winning writer is responsible for dispatch.
                        // Throwing rolls back the transaction (including the
                        // retry_count bump) and surfaces a conflict.
                        throw new org.springframework.transaction.TransactionSystemException(
                            "concurrent generation change for submission " + id
                            + " (expected gen " + expectedGen + " already moved)");
                    }
                } else {
                    // Already Pending (or unknown): outbox without a generation
                    // bump. F1: persist retry_count via the targeted CAS.
                    submissionMapper.bumpRetryCount(id, 1);
                    submission.setStatus("Pending");
                    writeRejudgeOutbox(submission,
                        submission.getGeneration() != null ? submission.getGeneration() : 1L);
                    dispatchWon[0] = true;
                }

                // F3 fix: defer the Redis enqueue to afterCommit. Enqueueing
                // inside the DB transaction let a worker consume the job
                // before the Pending/generation update committed. The outbox
                // insert above stays in-tx so the dispatch intent is durable.
                if (dispatchWon[0] && TransactionSynchronizationManager.isSynchronizationActive()) {
                    final Submission enqueueTarget = submission;
                    TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    queueService.enqueueJudgeJob(
                                        enqueueTarget.getId(),
                                        String.valueOf(enqueueTarget.getProblemId()),
                                        enqueueTarget.getUserId(),
                                        enqueueTarget.getLanguage(),
                                        enqueueTarget.getCode()
                                    );
                                } catch (Exception e) {
                                    // Redis down post-commit: the DB bump +
                                    // outbox row already committed, so the
                                    // row is recoverable by the reaper / a
                                    // future outbox replay. Log and continue.
                                    log.warn("Post-commit enqueue failed for submission {} "
                                            + "(Pending reset committed; outbox row recorded "
                                            + "for replay): {}",
                                            enqueueTarget.getId(), e.getMessage());
                                }
                            }
                        });
                } else if (dispatchWon[0]) {
                    // No active transaction synchronization (e.g. a direct
                    // unit-test call without a Spring tx manager): enqueue
                    // immediately so the behavior is not silently lost
                    // (matches the reaper's fallback).
                    queueService.enqueueJudgeJob(
                        submission.getId(),
                        String.valueOf(submission.getProblemId()),
                        submission.getUserId(),
                        submission.getLanguage(),
                        submission.getCode()
                    );
                }
            });

            result.setSuccess(true);
            result.setNewStatus(judgingAfterRejudge(submission) ? "Judging" : "Pending");
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Fenced rejudge initiated for submission {} (retryCount={}, gen={})",
                id, submission.getRetryCount(),
                submission.getGeneration() != null ? submission.getGeneration() : 1L);
        } catch (Exception e) {
            log.error("Failed fenced rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        if (result.getSuccess()) {
            AuditContext.setOldValues(Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(Map.of(
                "newStatus", result.getNewStatus() != null ? result.getNewStatus() : "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }

    /**
     * Whether the submission is (or will be observed as) JUDGING after the
     * fenced rejudge.
     *
     * @param submission the submission to inspect
     * @return {@code true} when the submission is in the JUDGING state
     */
    @Override
    public boolean judgingAfterRejudge(Submission submission) {
        SubmissionStatus current = SubmissionStatus.fromDbName(submission.getStatus());
        return current == SubmissionStatus.JUDGING;
    }

    /**
     * Write a shadow outbox row for a fenced rejudge dispatch, gated on the
     * outbox feature flag and a non-null mapper (ADR-003 M3a double-write).
     * The caller must pass the <b>post-mutation</b> generation so the recorded
     * outbox row points at the correct generation the dispatcher should
     * observe.
     *
     * @param submission       the submission after the CAS landed
     * @param postMutationGen  the generation to record on the outbox row
     */
    private void writeRejudgeOutbox(Submission submission, long postMutationGen) {
        if (!featureFlags.isUseJudgeOutbox() || judgeOutboxMapper == null) {
            return;
        }
        boolean portActive = featureFlags.getJudgeQueue().isUsePort();
        boolean isShadow = !portActive;
        judgeOutboxMapper.insert(JudgeOutboxRecord.of(
            submission, String.valueOf(submission.getProblemId()), postMutationGen, isShadow,
            uuidGenerator)
        );
    }
}
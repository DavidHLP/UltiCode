package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.fence.SubmissionStateMachine;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.policy.RejudgePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Write-side implementation of {@link AdminSubmissionService}.
 *
 * <p>After the ADR-0011 Stage 2 extraction, this service owns only the
 * submission rejudge state machine (single + batch, ADR-003 fenced outbox +
 * generation bump). Every read-side concern (paginated list, single detail,
 * statistics, filter options) moved behind
 * {@link com.ulticode.modules.admin.projection.AdminSubmissionProjection}.
 * Cross-module entity imports ({@code User}, {@code Problem}) and their
 * mappers have left this file &mdash; the projection owns them.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubmissionServiceImpl implements AdminSubmissionService {

    private final SubmissionMapper submissionMapper;
    private final QueueService queueService;
    /**
     * ADR-003 M3a outbox mapper for the rejudge double-write. Nullable so the
     * flag-off path (no outbox wiring in legacy tests) is unaffected.
     *
     * <p>P0 #11: under port cutover, the row becomes a <b>real</b> outbox row
     * for the dispatcher (port mode writes {@code is_shadow=0}, not
     * {@code is_shadow=1} as in the original M3a shadow double-write).
     * See {@link #writeRejudgeOutbox}.
     */
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    private final UuidGenerator uuidGenerator;
    /**
     * Programmatic transaction boundary for the M3b fenced rejudge path. Used
     * instead of {@code @Transactional} so the flag-off branch can stay
     * byte-for-byte identical to the legacy non-transactional rejudge.
     */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    /**
     * C2: the fenced rejudge state machine lifted out of this service. Owns
     * the generation CAS + lease revoke + afterCommit enqueue + outbox
     * double-write + 3-way status branch. The legacy non-transactional path
     * stays inline because, per the red team CR §3.2, fenced and legacy are
     * not the same shape — folding them into a single method would force
     * the policy to dispatch internally and lose the depth gain.
     */
    private final RejudgePolicy rejudgePolicy;

    @Override
    @Audited(action = AuditVocabulary.REQUEUE_SUBMISSION, entityType = AuditVocabulary.ENTITY_SUBMISSION, userIdFrom = "id")
    public RejudgeResult rejudge(String id, boolean notifyUser) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            RejudgeResult result = new RejudgeResult();
            result.setSubmissionId(id);
            result.setSuccess(false);
            result.setError("Submission not found");
            return result;
        }

        RejudgeResult result = new RejudgeResult();
        result.setSubmissionId(id);
        result.setOldStatus(submission.getStatus());

        // ADR-003 M3b: fenced rejudge path. When the generation fence flag is
        // off, fall through to the legacy non-transactional path so behavior is
        // byte-for-byte identical to the pre-fence implementation.
        if (!featureFlags.isUseGenerationFence()) {
            return rejudgeLegacy(submission, result);
        }
        return rejudgePolicy.rejudgeFenced(submission, result);
    }

    /**
     * Legacy rejudge path (pre-ADR-003). Non-transactional; on enqueue failure
     * the DB row stays Pending (orphan), matching the historical contract.
     * Preserved verbatim so flag-off deployments observe no behavior change.
     */
    private RejudgeResult rejudgeLegacy(Submission submission, RejudgeResult result) {
        String id = submission.getId();
        try {
            // Reset submission status to Pending for re-evaluation
            submission.setStatus("Pending");

            // D-23: Increment retry count to track rejudge attempts
            submission.setRetryCount(
                submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
            );
            submissionMapper.updateById(submission);

            // D-04: Enqueue after DB update to avoid orphaned jobs on DB failure
            queueService.enqueueJudgeJob(
                submission.getId(),
                String.valueOf(submission.getProblemId()),
                submission.getUserId(),
                submission.getLanguage(),
                submission.getCode()
            );

            result.setSuccess(true);
            result.setNewStatus("Pending");
            // Surface rejudge metadata to the caller so the admin UI can
            // detect that a rejudge actually happened even when old and
            // new status are identical (e.g. Pending -> Pending).
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Rejudge initiated for submission: {} (retryCount={})",
                id, submission.getRetryCount());
        // broad catch: all failures map to same error response
        } catch (Exception e) {
            log.error("Failed to enqueue rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        if (result.getSuccess()) {
            AuditContext.setOldValues(java.util.Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(java.util.Map.of(
                "newStatus", "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }

    /**
     * ADR-003 M3b fenced rejudge path. Runs the generation bump + outbox
     * double-write inside a single transaction (F7); the Redis enqueue is
     * deferred to an {@code afterCommit} callback (F3 fix) so a worker cannot
     * consume the job before the Pending/generation update commits.
     *
     * <p>Branching on observed status:
     * <ul>
     *   <li>Terminal status + {@link SubmissionStateMachine#canAdminRejudgeFrom}
     *       -> bump generation, reset to Pending, outbox. <b>F1:</b> retry_count
     *       is persisted via the targeted {@code bumpRetryCount} CAS — the
     *       branch must NOT call {@code updateById(submission)} because the
     *       in-memory entity still carries the stale terminal status + the
     *       pre-bump generation, and MyBatis-Plus's default {@code NOT_NULL}
     *       strategy would write them back over the Pending reset + new gen,
     *       leaving the worker unable to acquire its Pending lease. If the bump
     *       CAS loses a race ({@code bumped == 0}) the branch does NOT enqueue
     *       and returns a conflict (the winning writer owns the dispatch).</li>
     *   <li>JUDGING -> force lease expiry + revoke the attempt (F2); the lease
     *       reaper will atomically bump generation in its single transaction.
     *       This avoids racing the worker on the generation field.</li>
     *   <li>Other (e.g. already Pending) -> outbox without a bump.</li>
     * </ul>
     */
    private RejudgeResult rejudgeFenced(Submission submission, RejudgeResult result) {
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

                // Increment retry count regardless of branch (matches legacy). It
                // is persisted via the targeted bumpRetryCount CAS below — never
                // via updateById(submission), which would clobber the fence
                // columns (F1, C1).
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
                    // C1 fix: persist retry_count via a TARGETED update that touches
                    // ONLY retry_count. We must NOT call updateById here: the entity
                    // still holds the original future lease value read at rejudge()
                    // line 294 (forceLeaseExpiry ran via a DB CAS and did not refresh
                    // the entity), and MyBatis-Plus's default NOT_NULL update strategy
                    // would write that stale future lease back, silently undoing the
                    // forced expiry and leaving the row stuck in JUDGING forever.
                    // Status stays Judging until the reaper flips it to Pending.
                    submissionMapper.bumpRetryCount(id, 1);
                    // JUDGING branch never dispatches here — the reaper bumps the
                    // generation and dispatches from its own single transaction.
                    // Outbox write is intentionally SKIPPED (see H2 note below).
                } else if (rejudgeable) {
                    // Terminal -> bump generation atomically, reset to Pending.
                    long expectedGen = submission.getGeneration() != null ? submission.getGeneration() : 1L;
                    long newGen = expectedGen + 1;
                    int bumped = submissionMapper.bumpGenerationAndReset(id, expectedGen, newGen);
                    boolean bumpWon = bumped == 1;
                    // F1 fix: retry_count via targeted CAS — NOT updateById. The
                    // bumpGenerationAndReset CAS already set status='Pending' +
                    // generation=newGen + cleared lease columns in the DB; an
                    // updateById(submission) here would write the entity's stale
                    // terminal status + old generation back (NOT_NULL strategy),
                    // un-doing the Pending reset so the worker's acquireLease
                    // (WHERE status='Pending') fails and the rejudge is lost.
                    submissionMapper.bumpRetryCount(id, 1);
                    if (bumpWon) {
                        submission.setGeneration(newGen);
                        // H2 fix: only write the outbox shadow when the generation
                        // bump actually won. A race-lost bump means the row's real
                        // generation already moved; the winning writer owns the
                        // outbox row at the new gen.
                        writeRejudgeOutbox(submission, newGen);
                        dispatchWon[0] = true;
                    } else {
                        // F1: the bump CAS lost a race (another reaper / rejudge
                        // already bumped). Do NOT enqueue — the winning writer is
                        // responsible for the dispatch. Throwing here rolls back
                        // the transaction (including the retry_count bump) and
                        // surfaces a conflict to the caller, so no orphan job is
                        // created and no duplicate outbox row pollutes the diff.
                        throw new org.springframework.transaction.TransactionSystemException(
                            "concurrent generation change for submission " + id
                            + " (expected gen " + expectedGen + " already moved)");
                    }
                } else {
                    // Already Pending (or unknown): outbox without a generation bump.
                    // F1: persist retry_count via the targeted CAS, NOT updateById.
                    submissionMapper.bumpRetryCount(id, 1);
                    submission.setStatus("Pending");
                    writeRejudgeOutbox(submission,
                        submission.getGeneration() != null ? submission.getGeneration() : 1L);
                    dispatchWon[0] = true;
                }

                // F3 fix: defer the Redis enqueue to afterCommit. Enqueueing
                // inside the DB transaction let a worker consume the job before
                // the Pending/generation update committed: the worker would read
                // the stale terminal row, fail acquireLease, and permanently
                // discard the only job. Moving the enqueue to afterCommit
                // (mirroring the lease reaper's H1 pattern) guarantees the
                // worker only ever sees the post-commit Pending row. The outbox
                // insert above stays in-tx so the dispatch intent is durable.
                if (dispatchWon[0]
                        && org.springframework.transaction.support.TransactionSynchronizationManager
                                .isSynchronizationActive()) {
                    final Submission enqueueTarget = submission;
                    org.springframework.transaction.support.TransactionSynchronizationManager
                        .registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
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
                                        // outbox row already committed, so the row
                                        // is recoverable by the reaper / a future
                                        // outbox replay. Log and continue so one
                                        // bad enqueue does not abort the rejudge.
                                        log.warn("Post-commit enqueue failed for submission {} "
                                                + "(Pending reset committed; outbox row recorded "
                                                + "for replay): {}",
                                                enqueueTarget.getId(), e.getMessage());
                                    }
                                }
                            });
                } else if (dispatchWon[0]) {
                    // No active transaction synchronization (e.g. a direct unit-test
                    // call without a Spring tx manager): enqueue immediately so the
                    // behavior is not silently lost (matches the reaper's fallback).
                    queueService.enqueueJudgeJob(
                        submission.getId(),
                        String.valueOf(submission.getProblemId()),
                        submission.getUserId(),
                        submission.getLanguage(),
                        submission.getCode()
                    );
                }
                // JUDGING-branch outbox note (H2): we do NOT write an outbox row
                // here because the submission's generation has not been bumped
                // (the reaper does that). The only correct outbox row would be
                // at the post-reaper generation, which the reaper itself writes.
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
            AuditContext.setOldValues(java.util.Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(java.util.Map.of(
                "newStatus", result.getNewStatus() != null ? result.getNewStatus() : "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }

    /**
     * Whether the submission is in (or will be observed as) JUDGING after the
     * fenced rejudge. Used to surface an accurate newStatus to the caller; the
     * JUDGING branch keeps the row Judging until the reaper flips it.
     */
    private boolean judgingAfterRejudge(Submission submission) {
        SubmissionStatus current = SubmissionStatus.fromDbName(submission.getStatus());
        return current == SubmissionStatus.JUDGING;
    }

    /**
     * Write a shadow outbox row for a fenced rejudge dispatch, gated on the
     * outbox feature flag and a non-null mapper (ADR-003 M3a double-write). The
     * caller must pass the <b>post-mutation</b> generation so the recorded
     * dispatch intent matches the real delivery generation (H2 fix: a stale
     * pre-bump generation would pollute the M3c shadow-comparator "diff=0"
     * gate).
     *
     * <p>The unique key {@code (submission_id, generation)} makes a duplicate
     * insert (e.g. a concurrent rejudge that already wrote this gen) throw
     * rather than silently double-record; that exception propagates and rolls
     * the transaction, which is the desired fail-loud behavior for a real
     * invariant violation.
     *
     * @param submission the submission (post-mutation, with the correct generation)
     * @param generation the post-mutation generation to record
     */
    private void writeRejudgeOutbox(Submission submission, long generation) {
        if (featureFlags.isUseJudgeOutbox() && judgeOutboxMapper != null) {
            // P0 #11 fix: `is_shadow = !portActive`. Under port cutover
            // the dispatcher ignores shadow rows, so a hard-coded `true`
            // would strand the rejudged submission Pending forever. Port
            // mode now writes a real row the dispatcher enqueues; shadow
            // mode keeps the original double-write observation behaviour.
            boolean portActive = featureFlags.getJudgeQueue().isUsePort();
            judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                    submission, String.valueOf(submission.getProblemId()), generation, !portActive, uuidGenerator));
            // Canary observability (P0 #11): log every successful admin
            // rejudge outbox insert with is_shadow / portActive pair. Logged
            // AFTER the insert call so failures (unique key conflicts) stay
            // on the existing log.warn path; do NOT move inside any
            // try/catch above.
            log.info("admin_rejudge.outbox.insert submissionId={} problemId={} generation={} is_shadow={} portActive={}",
                    submission.getId(), submission.getProblemId(), generation, !portActive, portActive);
        }
    }

    @Override
    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        // Non-null, non-empty, and size<=50 are enforced by Bean Validation
        // on the controller (see BatchRejudgeRequest @NotEmpty/@Size and
        // @Valid on the @RequestBody), so we can drop the silent null/empty
        // branch that previously masked client bugs.
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(submissionIds.size());
        response.setResults(new ArrayList<>(submissionIds.size()));
        int successful = 0;
        int failed = 0;

        for (String id : submissionIds) {
            RejudgeResult result = rejudge(id, notifyUsers);
            response.getResults().add(result);
            if (result.getSuccess()) {
                successful++;
            } else {
                failed++;
            }
        }

        response.setSuccessful(successful);
        response.setFailed(failed);
        return response;
    }

}

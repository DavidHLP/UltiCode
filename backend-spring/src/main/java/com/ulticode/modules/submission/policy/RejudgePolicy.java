package com.ulticode.modules.submission.policy;

import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;

/**
 * Write policy that owns the ADR-003 M3b fenced rejudge state machine.
 *
 * <p>Extracted out of {@code AdminSubmissionServiceImpl.rejudgeFenced} in the
 * C2 split because the distributed-correctness logic (generation CAS,
 * lease expiry + revoke, afterCommit enqueue, outbox double-write, 3-way
 * status branch) is submission-domain logic, not admin-module logic.
 *
 * <p>The legacy non-transactional rejudge path
 * ({@code AdminSubmissionServiceImpl.rejudgeLegacy}) intentionally stays in
 * the service — per the red team CR §3.2, fenced and legacy are <em>not</em>
 * the same shape (CAS fence vs direct write, in/out of transaction), so
 * folding them into a single method would force the policy to dispatch
 * internally and lose the depth gain.
 *
 * @author ulticode
 */
public interface RejudgePolicy {

    /**
     * Run the fenced rejudge flow on {@code submission}. Mutates
     * {@code submission.setGeneration(...)}, {@code submission.setStatus(...)}
     * and {@code submission.setRetryCount(...)} as appropriate, and writes
     * the post-commit outbox row when the generation bump wins.
     *
     * @param submission the submission to rejudge (must be non-null)
     * @param result     the result DTO to populate with {@code success},
     *                   {@code error}, {@code newStatus}, {@code rejudgedAt},
     *                   {@code retryCount}
     * @return the same {@code result} DTO with success/error populated
     */
    RejudgeResult rejudgeFenced(Submission submission, RejudgeResult result);

    /**
     * Whether the submission is (or will be observed as) {@code Judging}
     * after the fenced rejudge. Used by the service to surface an accurate
     * {@code newStatus} to the caller — the JUDGING branch keeps the row
     * {@code Judging} until the lease reaper flips it to {@code Pending}.
     *
     * @param submission the submission to inspect (must be non-null)
     * @return {@code true} when the submission is in the JUDGING state
     */
    boolean judgingAfterRejudge(Submission submission);
}
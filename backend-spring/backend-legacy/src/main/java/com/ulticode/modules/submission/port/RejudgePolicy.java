package com.ulticode.modules.submission.port;

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
 * <p>The legacy non-transactional rejudge path lives in
 * {@link LegacyRejudgeStrategy}; this policy selects between fenced and
 * legacy via {@link #rejudge(Submission, RejudgeResult)}, branching on the
 * {@code useGenerationFence} feature flag. Both strategies are reachable
 * through this one port, so the admin submission service is a 3-line
 * dispatch and tests can drive either branch without touching the service.
 * The fenced vs legacy "not the same shape" invariant from red-team
 * CR &sect;3.2 is preserved: the two paths remain separate classes with
 * separate transaction semantics; the policy selects, it does not merge.
 *
 * @author ulticode
 */
public interface RejudgePolicy {

    /**
     * Select the rejudge strategy based on the {@code useGenerationFence}
     * feature flag and run it on {@code submission}.
     *
     * <p>This is the single dispatch port the admin submission service
     * calls. Flag-on delegates to the fenced state machine; flag-off
     * delegates to {@link LegacyRejudgeStrategy}.
     *
     * @param submission the submission to rejudge (must be non-null)
     * @param result     the result DTO to populate
     * @return the same {@code result} DTO with success/error populated
     */
    RejudgeResult rejudge(Submission submission, RejudgeResult result);

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
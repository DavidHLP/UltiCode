package com.ulticode.modules.submission.port;

/**
 * Port through which the submission module asks the contest module to record
 * the contest-side effects of a submission, without the submission module
 * ever importing contest entities or mappers.
 *
 * <p>This inverts the pre-2026-07-02 coupling, where
 * {@code SubmissionServiceImpl} held four contest-module mappers
 * ({@code ContestProblemMapper}, {@code ContestMapper},
 * {@code ContestParticipantMapper}, {@code ContestSubmissionMapper}) and
 * inlined the contest-recording logic (see ADR-0001). The submission module
 * owns this port (it defines the collaboration it needs); the contest module
 * supplies the adapter ({@code ContestSubmissionAdapter}).
 *
 * <p>Both methods are synchronous so they share the caller's
 * {@code @Transactional} boundary (design D-04: "submission + contest record
 * in the same transaction"). The post-commit scoring path is handled
 * separately and asynchronously by {@code SubmissionJudgedEvent} →
 * {@code ContestScoringListener}; this port covers only the synchronous
 * same-transaction recording.
 *
 * <p>Dependency category: <b>cross-module</b>. The seam is real — the
 * submission aggregate and the contest aggregate are persisted by different
 * mappers and there is exactly one provider today (the contest module's
 * adapter) with the option of a no-op fake in submission-only tests.
 */
public interface ContestSubmissionPort {

    /**
     * If the user is participating in a RUNNING contest that contains the
     * supplied problem with a STARTED participation, record a
     * {@code ContestSubmission} row for it (design D-04 / D-05 / D-06).
     *
     * <p>Records at most one contest submission (the first matching active
     * contest) and marks the contest ranking dirty for the next realtime
     * flush. <b>Fire-and-forget semantics</b>: the caller wraps the call in
     * a try/catch so a contest-recording failure is logged but never breaks
     * the main submission — contest recording is supplementary.
     *
     * @param submissionId the just-created submission id
     * @param userId       the submitting user id
     * @param problemId    the problem id the submission targets
     */
    void recordSubmissionIfNeeded(String submissionId, String userId, Long problemId);

    /**
     * Whether the supplied submission belongs to a virtual-contest replay
     * (design R6.3 / F-08). Used by the submission module to skip achievement
     * triggers for virtual replays, which are not part of a user's earned
     * history.
     *
     * @param submissionId the submission id
     * @return {@code true} if the submission is a virtual-contest replay;
     *         {@code false} otherwise (including when the submission is not
     *         part of any contest)
     */
    boolean isVirtualParticipation(String submissionId);

    /**
     * Resolve the contest a submission belongs to, if any. Used by the judge
     * worker to route the verdict push to the right realtime contest channel.
     *
     * <p><b>Supplementary / fail-soft semantics</b>, matching the rest of this
     * port: returns {@code null} when the submission is not part of any contest
     * <em>or</em> when the contest lookup itself fails. The contract is that
     * this method never throws — a contest-schema hiccup must not surface as a
     * judge-worker failure, so the caller can treat the result as a plain
     * nullable string without its own try/catch.
     *
     * @param submissionId the submission id
     * @return the owning contest id, or {@code null} if the submission is not
     *         contest-bound or the lookup failed
     */
    String findContestId(String submissionId);
}

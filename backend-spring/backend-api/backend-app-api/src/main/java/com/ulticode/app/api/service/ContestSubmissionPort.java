package com.ulticode.app.api.service;

/**
 * Port through which the submission module asks the contest module to record
 * the contest-side effects of a submission, without the submission module
 * ever importing contest entities or mappers.
 *
 * <p>The submission module owns this port (it defines the collaboration it
 * needs); the contest module supplies the adapter.
 *
 * <p>Both methods are synchronous so they share the caller's
 * {@code @Transactional} boundary.
 */
public interface ContestSubmissionPort {

    /**
     * If the user is participating in a RUNNING contest that contains the
     * supplied problem with a STARTED participation, record a
     * {@code ContestSubmission} row for it.
     *
     * <p><b>Fire-and-forget semantics</b>: the caller wraps the call in a
     * try/catch so a contest-recording failure is logged but never breaks
     * the main submission.
     *
     * @param submissionId the just-created submission id
     * @param userId       the submitting user id
     * @param problemId    the problem id the submission targets
     */
    void recordSubmissionIfNeeded(String submissionId, String userId, Long problemId);

    /**
     * Whether the supplied submission belongs to a virtual-contest replay.
     * Used by the submission module to skip achievement triggers for virtual
     * replays.
     *
     * @param submissionId the submission id
     * @return {@code true} if virtual-contest replay; {@code false} otherwise
     */
    boolean isVirtualParticipation(String submissionId);

    /**
     * Resolve the contest a submission belongs to, if any. Used by the judge
     * worker to route the verdict push to the right realtime contest channel.
     *
     * <p><b>Supplementary / fail-soft semantics</b>: returns {@code null} when
     * the submission is not part of any contest or the lookup failed.
     *
     * @param submissionId the submission id
     * @return the owning contest id, or {@code null}
     */
    String findContestId(String submissionId);
}

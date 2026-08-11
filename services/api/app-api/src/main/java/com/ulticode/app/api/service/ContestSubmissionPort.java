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
     * Record contest effects only when an explicit contest context is present.
     * A missing {@code contestId} means an ordinary submission and is a no-op;
     * this port must never scan currently running contests to infer ownership.
     *
     * <p>With a contest context, admission failures are propagated so the
     * caller's submission transaction rolls back. The adapter validates the
     * contest, problem, participant, virtual session and deadline while holding
     * the contest/participant locks.
     *
     * @param submissionId the just-created submission id
     * @param userId the submitting user id
     * @param problemId the problem id the submission targets
     * @param contestId explicit contest context, or {@code null}
     * @param virtualSessionId explicit virtual session context, or {@code null}
     */
    void recordSubmissionIfNeeded(String submissionId, String userId, Long problemId,
                                  String contestId, String virtualSessionId);

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
     * Whether the submission has an explicit contest mapping.
     *
     * <p>This lookup is strict: failures propagate so the submission rejudge
     * policy cannot silently bypass the contest safety rule.</p>
     */
    boolean isContestSubmission(String submissionId);

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

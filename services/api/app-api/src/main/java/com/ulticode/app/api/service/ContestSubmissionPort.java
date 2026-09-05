package com.ulticode.app.api.service;

/**
 * Port through which the submission module asks the contest module to record
 * the contest-side effects of a submission, without the submission module
 * ever importing contest entities or mappers.
 *
 * <p>The submission module owns this port (it defines the collaboration it
 * needs); the contest module supplies the adapter.
 *
 * <p>Remote contest intake uses the separate durable event path on the
 * Submission owner ({@code SubmissionCreated} outbox). The submission
 * module owns contest association for all production write paths; the
 * {@code recordSubmissionIfNeeded} method that existed on this port has
 * been removed (P3-CONTRACT-004) as it had zero production callers.
 */
public interface ContestSubmissionPort {

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

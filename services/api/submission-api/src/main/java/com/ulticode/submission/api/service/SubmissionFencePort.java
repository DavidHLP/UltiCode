package com.ulticode.submission.api.service;

/**
 * Read + mutate surface for the ADR-003 judge lease fence — the half of the
 * submission state machine the judge worker drives while a verdict is in flight.
 *
 * <p>{@link SubmissionVerdictWritePort} owns status/verdict writes and
 * {@link SubmissionIntakePort} owns intake. This port owns the
 * <em>in-flight</em> fence axis: reading the
 * generation a worker observed, CAS-acquiring the lease before judging starts,
 * and renewing it from the heartbeat so a slow sandbox run does not lose its
 * hold on the row.
 *
 * <p>Dependency category: <b>local or Dubbo RPC</b>. The Submission owner
 * exposes the default adapter ({@code DefaultSubmissionFencePort}) through
 * the {@code backend-submission} group; the independent Judge Worker
 * consumes the same port remotely. The pre-cutover compatibility route may
 * temporarily delegate to App. Tests can substitute a fake.
 *
 * @author ulticode
 */
public interface SubmissionFencePort {

    /**
     * Current generation of the submission row this worker is about to judge.
     * Returns {@code null} if the row does not exist. A null
     * {@code generation} column resolves to {@code 1L} so a worker never
     * observes a null fence axis.
     *
     * @param submissionId the submission id
     * @return the generation to fence on; null if the submission is gone
     */
    Long currentGeneration(String submissionId);

    /**
     * CAS-acquire the judge lease (ADR-003 §2.3). Succeeds iff the row is still
     * {@code Pending} at the supplied generation; a bump (rejudge / reaper) or a
     * rival worker that already leased it makes this return {@code false}.
     *
     * @param submissionId submission id
     * @param attemptId    attempt UUID held by the worker (fence axis 2)
     * @param generation   generation the worker observed (fence axis 1)
     * @param ttlSeconds   lease time-to-live in seconds
     * @return {@code true} iff the lease was acquired
     */
    boolean acquireLease(String submissionId, String attemptId, long generation, long ttlSeconds);

    /**
     * Renew a held lease from the heartbeat. Returns {@code false} once the
     * lease was lost (superseded, reaped, or expired) so the heartbeat stops
     * fighting for a row it no longer owns.
     *
     * @param submissionId submission id
     * @param attemptId    attempt UUID held by the worker
     * @param ttlSeconds   lease time-to-live in seconds
     * @return {@code true} iff the lease is still held
     */
    boolean renewLease(String submissionId, String attemptId, long ttlSeconds);
}

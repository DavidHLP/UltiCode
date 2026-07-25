package com.ulticode.modules.submission.port;

import java.util.Optional;

/**
 * Read + mutate surface for the ADR-003 judge lease fence — the half of the
 * submission state machine the judge worker drives while a verdict is in
 * flight.
 *
 * <p>{@link SubmissionWritePort} owns the <em>terminal</em> writes (intake +
 * verdict). This port owns the <em>in-flight</em> fence axis: reading the
 * generation a worker observed, CAS-acquiring the lease before judging starts,
 * and renewing it from the heartbeat so a slow sandbox run does not lose its
 * hold on the row. Collapsing the three operations behind one narrow seam keeps
 * the judge attempt executor free of the {@code Submission} entity and the raw
 * {@code SubmissionMapper} (architecture-review candidate #2): the consumer
 * asks for a {@code long} generation and a {@code boolean} outcome instead of a
 * domain object and an affected-row count.
 *
 * <p>Dependency category: <b>in-process</b>. The default adapter
 * ({@code DefaultSubmissionFencePort}) is the only provider today and delegates
 * to {@code SubmissionMapper}; tests can substitute a fake. Behaviour matches
 * the pre-deepening executor byte-for-byte: a missing row yields
 * {@link Optional#empty()}, a row whose {@code generation} column is null
 * resolves to {@code 1L}, and the two CAS ops report success iff exactly one
 * row matched.
 *
 * @author ulticode
 */
public interface SubmissionFencePort {

    /**
     * Current generation of the submission, or {@link Optional#empty()} if the
     * row does not exist. A null {@code generation} column resolves to
     * {@code 1L} so a worker never observes a null fence axis.
     *
     * @param submissionId the submission id
     * @return the generation to fence on; empty if the submission is gone
     */
    Optional<Long> currentGeneration(String submissionId);

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

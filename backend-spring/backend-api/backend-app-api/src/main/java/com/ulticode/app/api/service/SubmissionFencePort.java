package com.ulticode.app.api.service;

import java.util.Optional;

/**
 * Read + mutate surface for the ADR-003 judge lease fence — the half of the
 * submission state machine the judge worker drives while a verdict is in flight.
 */
public interface SubmissionFencePort {

    /**
     * Current generation of the submission row this worker is about to judge.
     * Returns {@code Optional.empty()} if the row does not exist.
     */
    Optional<Long> currentGeneration(String submissionId);

    /**
     * CAS-acquire the judge lease before judging starts.
     * Succeeds iff the current generation matches the expected value.
     *
     * @return {@code true} if the lease was acquired; {@code false} if a
     *         concurrent worker already holds it (stale generation)
     */
    boolean acquireLease(String submissionId, long expectedGeneration);

    /**
     * Renew an existing lease from the heartbeat.
     *
     * @return {@code true} if the lease was renewed; {@code false} if the
     *         lease was revoked or the row vanished
     */
    boolean renewLease(String submissionId, long expectedGeneration);
}

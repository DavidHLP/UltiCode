package com.ulticode.modules.contest.service;

/**
 * Contest lifecycle module — owns contest-level participant status
 * transitions and the relational cleanup that follows a contest soft-delete.
 *
 * <p>This is the natural home for behavior that is about a contest's lifetime
 * rather than about a single verdict:
 * <ul>
 *   <li><b>P0-2</b> batch-transition REGISTERED participants to STARTED when a
 *       contest moves into RUNNING.</li>
 *   <li><b>P1-2 / P2-2</b> auto-finish virtual participants whose
 *       {@code started_at + duration} has elapsed.</li>
 *   <li><b>P2-5</b> cascade-delete a soft-deleted contest's relational rows
 *       (participants, problems, submissions, problem results, first-solve
 *       records).</li>
 * </ul>
 *
 * <p>Verdict application and scoring invariants live in the deep
 * {@link ContestAdjudicationService}; this module does not score. Every
 * method is idempotent and safe to retry.
 */
public interface ContestLifecycleService {

    /**
     * Transition all REGISTERED participants of a contest to STARTED, stamping
     * {@code started_at} to {@code now}. Idempotent.
     *
     * @param contestId the contest whose participants to start
     * @return number of participants transitioned
     */
    int batchStartParticipants(String contestId);

    /**
     * Auto-finish virtual participants whose time has expired
     * ({@code started_at + duration_minutes < now}). Idempotent.
     *
     * @return number of participants transitioned
     */
    int autoFinishVirtualParticipants();

    /**
     * Cascade-delete a soft-deleted contest's relational rows. Idempotent:
     * a missing or already-deleted contest is a no-op.
     *
     * @param contestId the contest to clean up
     */
    void deleteContestCascade(String contestId);
}

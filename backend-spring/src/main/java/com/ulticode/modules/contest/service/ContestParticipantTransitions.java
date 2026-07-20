package com.ulticode.modules.contest.service;

import java.time.LocalDateTime;

/**
 * Single seam for all {@link com.ulticode.modules.contest.entity.ContestParticipant}
 * status transitions.
 * <p>The scheduled path ({@link com.ulticode.modules.contest.service.ContestLifecycleService})
 * delegates bulk transitions here so that conditional-UPDATE guards, clock arithmetic,
 * and side-effect ordering have one locality. Individual participant transitions
 * (interactive start/finish) are handled directly by the mapper in
 * {@code ContestParticipationService}.
 *
 * <p>Load-bearing constraints preserved:
 * <ul>
 *   <li>D-05/D-06 — status rules are expressed as conditional-UPDATE guards</li>
 *   <li>R6.2/F-06 — effective time arithmetic stays in {@link com.ulticode.modules.contest.clock.ContestClock}</li>
 *   <li>D-04 — submission intake is not modified by this module</li>
 * </ul>
 */
public interface ContestParticipantTransitions {

    /**
     * Bulk-transition all REGISTERED participants for a contest to STARTED.
     * Used by the lifecycle tick when a contest transitions UPCOMING → RUNNING.
     *
     * @param contestId the contest
     * @param now       current timestamp
     * @return number of rows updated
     */
    int batchStartParticipants(String contestId, LocalDateTime now);

    /**
     * Bulk-transition all STARTED real (non-virtual) participants for a contest to FINISHED.
     * Used by the lifecycle tick when a contest transitions RUNNING → FINISHED.
     *
     * @param contestId the contest
     * @param now       current timestamp
     * @return number of rows updated
     */
    int finishStartedRealParticipants(String contestId, LocalDateTime now);

    /**
     * Bulk-transition a set of virtual participant ids to FINISHED.
     * Used by the lifecycle tick for expired virtual sessions and by
     * {@code finishVirtualContest} for user-initiated completion.
     *
     * @param participantIds the participant row ids
     * @param now           current timestamp
     * @return number of rows updated
     */
    int bulkFinishVirtualByIds(java.util.Collection<String> participantIds, LocalDateTime now);
}

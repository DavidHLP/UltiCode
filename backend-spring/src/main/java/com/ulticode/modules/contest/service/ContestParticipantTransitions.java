package com.ulticode.modules.contest.service;

import java.time.LocalDateTime;

/**
 * Single seam for all {@link com.ulticode.modules.contest.entity.ContestParticipant}
 * status transitions.
 * <p>Callers: the scheduled lifecycle path
 * ({@link com.ulticode.modules.contest.service.ContestLifecycleService})
 * delegates all three bulk transitions here, and the interactive
 * virtual-finish path ({@code ContestParticipationServiceImpl#finishVirtualContest})
 * routes {@link #bulkFinishVirtualByIds} through this seam too (with a
 * single-element id list) so both paths exercise the same transition code.
 * Individual real-participant start/finish (interactive register to start,
 * single real participant finishing) still goes direct to the mapper in
 * {@code ContestParticipationService}; only the virtual-finish overlap
 * crosses this seam today.
 *
 * <p>Purpose: this seam is the concentration point for participant status
 * transitions. Today it forwards to the mapper with input hygiene
 * (null/empty guard + HashSet dedup for the virtual batch) and the canonical
 * status literals; the transition rules
 * themselves (conditional-UPDATE WHERE guards) stay in the mapper SQL so
 * they remain atomic against scheduler / rejudge races. The seam exists so
 * future transition-side preconditions land in one place rather than
 * across the two callers; see the M2 / R6.2 notes on those callers.
 *
 * <p>Load-bearing constraints preserved:
 * <ul>
 *   <li>D-05/D-06 — status rules are enforced as conditional-UPDATE guards in the mapper SQL</li>
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

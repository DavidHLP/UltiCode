package com.ulticode.modules.contest.port;

/**
 * Mark-dirty port the contest module uses to flag a contest's ranking
 * for the next {@code RealtimeService.flushPendingRankings} tick.
 *
 * <p>Replaces two cross-module leak points the contest module had on
 * {@code com.ulticode.modules.websocket.service.RealtimeService.markDirty}:
 * <ul>
 *   <li>{@code ContestSubmissionAdapter.recordSubmissionIfNeeded} —
 *       marks dirty when a new contest submission is recorded.</li>
 *   <li>{@code ContestScheduler.transitionToRunning} — marks dirty so the
 *       initial ranking appears on the leaderboard immediately.</li>
 * </ul>
 *
 * <p>The mark-dirty flag is intentionally decoupled from the throttle +
 * flush logic that lives in {@code RealtimeService.flushPendingRankings}.
 * That throttling/flush lives in the producer side and is candidate for
 * collapse into the contest ranking adapter in a follow-up (see ADR-0009).
 *
 * <p>Contract: best-effort, fire-and-forget. Missing the mark just means
 * the ranking is refreshed on the next natural event.
 *
 * @author ulticode
 */
public interface ContestRankingMarkDirtyPort {

    /**
     * Flag a contest's ranking for the next flush tick.
     *
     * @param contestId the contest id (must not be {@code null})
     */
    void markDirty(String contestId);
}
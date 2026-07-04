package com.ulticode.modules.contest.port;

import com.ulticode.modules.contest.entity.enums.ContestStatus;

import java.time.Instant;

/**
 * Status-push port the contest module uses to broadcast a contest
 * lifecycle transition to its WebSocket subscribers.
 *
 * <p>Replaces two cross-module leak points the contest module had on
 * {@code com.ulticode.modules.websocket.service.RealtimeService.emitContestStatus}:
 * <ul>
 *   <li>{@code ContestScheduler.transitionToRunning} — broadcasts
 *       {@code RUNNING} with the contest's actual start time.</li>
 *   <li>{@code ContestScheduler.transitionToFinished} — broadcasts
 *       {@code FINISHED} with the contest's actual end time.</li>
 * </ul>
 *
 * <p>The port accepts the contest module's own {@link ContestStatus} enum
 * (not the websocket-side wire enum) so the consumer stays decoupled from
 * the wire format. The adapter maps the contest-side enum to the wire
 * format; if the wire format later needs to change (e.g. add a
 * {@code PAUSED} state) only the adapter moves.
 *
 * <p>Contract: best-effort, fire-and-forget. The DB row carries the
 * durable status; the WebSocket push is the live leaderboard signal.
 *
 * @author ulticode
 */
public interface ContestStatusPushPort {

    /**
     * Broadcast a contest status transition to the contest room.
     *
     * <p>Implementations MUST NOT throw on a missing subscription.
     *
     * @param contestId the contest id (must not be {@code null})
     * @param status    the contest's new lifecycle status (must not be {@code null})
     * @param startedAt the contest's actual start time, or {@code null} if not yet started
     * @param endsAt    the contest's actual end time, or {@code null} if not yet ended
     * @param message   an optional human-readable message, or {@code null}
     */
    void emitStatus(String contestId, ContestStatus status,
                    Instant startedAt, Instant endsAt, String message);
}
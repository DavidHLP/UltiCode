package com.ulticode.modules.websocket.port;

/**
 * Status-push port for broadcasting a contest lifecycle transition to
 * WebSocket subscribers.
 *
 * <p>Uses {@code String statusName} (not the contest-domain enum) so the
 * contract module stays free of domain-module dependencies. Promoted from
 * {@code com.ulticode.modules.contest.port.ContestStatusPushPort} during
 * P7-RELOCATE-CONTEST-001.
 *
 * @author ulticode
 */
public interface ContestStatusPushPort {

    /**
     * Broadcast a contest status transition to the contest room.
     *
     * <p>Implementations MUST NOT throw on a missing subscription.
     *
     * @param contestId  the contest id (must not be {@code null})
     * @param statusName the contest's new lifecycle status name (UPCOMING/RUNNING/FINISHED)
     * @param startedAtEpochMillis the contest's actual start time in epoch-millis, or {@code null}
     * @param endsAtEpochMillis    the contest's actual end time in epoch-millis, or {@code null}
     * @param message   an optional human-readable message, or {@code null}
     */
    void emitStatus(String contestId, String statusName,
                    Long startedAtEpochMillis, Long endsAtEpochMillis, String message);
}

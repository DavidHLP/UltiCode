package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.port.ContestStatusPushPort;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * STOMP adapter of {@link ContestStatusPushPort}.
 *
 * <p>Maps the contest module's own {@link ContestStatus} enum to the
 * websocket wire-format
 * {@code com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus}:
 * <ul>
 *   <li>{@code RUNNING} → wire {@code RUNNING}</li>
 *   <li>{@code FINISHED} → wire {@code ENDED}</li>
 *   <li>other states ({@code DRAFT}, {@code UPCOMING}, {@code CANCELLED}) are
 *       ignored — no wire push</li>
 * </ul>
 *
 * <p>This translation is the only place that knows about both enums. A
 * future wire-format change touches this one file; a future contest
 * lifecycle change (e.g. add {@code PAUSED}) touches this one file.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketContestStatusPushAdapter implements ContestStatusPushPort {

    private final RealtimeService realtimeService;

    @Override
    public void emitStatus(String contestId, ContestStatus status,
                           Instant startedAt, Instant endsAt, String message) {
        com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus wire = toWireStatus(status);
        if (wire == null) {
            // DRAFT, UPCOMING, CANCELLED never produce a wire push. Silent
            // skip — matches the legacy RealtimeService.emitContestStatus
            // contract (no exception for unhandled states).
            return;
        }
        realtimeService.emitContestStatus(contestId, wire, startedAt, endsAt, message);
    }

    private static com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus toWireStatus(ContestStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case RUNNING -> com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus.RUNNING;
            case FINISHED -> com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus.ENDED;
            default -> null;
        };
    }
}
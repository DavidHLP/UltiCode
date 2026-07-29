package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.port.ContestStatusPushPort;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import com.ulticode.modules.websocket.util.WebSocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * STOMP adapter of {@link ContestStatusPushPort}.
 *
 * <p>Post-Candidate-4: owns the {@code SimpMessagingTemplate} call
 * directly. Also owns the contest→wire enum translation
 * (RUNNING→RUNNING, FINISHED→ENDED; other states are silently skipped).
 *
 * @author ulticode
 */
@Component
public class WebSocketContestStatusPushAdapter implements ContestStatusPushPort {

    private static final Logger log = LoggerFactory.getLogger(WebSocketContestStatusPushAdapter.class);

    private final WebSocketBroadcastBridge broadcastBridge;

    public WebSocketContestStatusPushAdapter(WebSocketBroadcastBridge broadcastBridge) {
        this.broadcastBridge = broadcastBridge;
    }

    @Override
    public void emitStatus(String contestId, ContestStatus status,
                           Instant startedAt, Instant endsAt, String message) {
        ContestStatusEvent.ContestStatus wire = toWireStatus(status);
        if (wire == null) {
            // DRAFT, UPCOMING, CANCELLED never produce a wire push.
            return;
        }
        ContestStatusEvent event = new ContestStatusEvent(contestId, wire, startedAt, endsAt, message);
        String destination = WebSocketUtils.getContestRoomName(contestId) + "/status";
        broadcastBridge.send(destination, event);
        log.info("Contest {} status changed to: {}", contestId, wire);
    }

    private static ContestStatusEvent.ContestStatus toWireStatus(ContestStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case RUNNING -> ContestStatusEvent.ContestStatus.RUNNING;
            case FINISHED -> ContestStatusEvent.ContestStatus.ENDED;
            default -> null;
        };
    }
}
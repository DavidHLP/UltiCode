package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.app.api.service.ContestStatusPushPort;
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
 * <p>P7-RELOCATE-CONTEST-001: now implements the app-api port interface
 * (String statusName + Long epoch-millis replaces ContestStatus enum +
 * Instant) so the contest module can call the port without importing
 * the legacy websocket package.
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
    public void emitStatus(String contestId, String statusName,
                           Long startedAtEpochMillis, Long endsAtEpochMillis, String message) {
        ContestStatusEvent.ContestStatus wire = toWireStatus(statusName);
        if (wire == null) {
            // DRAFT, UPCOMING, CANCELLED never produce a wire push.
            return;
        }
        Instant startedAt = startedAtEpochMillis != null ? Instant.ofEpochMilli(startedAtEpochMillis) : null;
        Instant endsAt = endsAtEpochMillis != null ? Instant.ofEpochMilli(endsAtEpochMillis) : null;
        ContestStatusEvent event = new ContestStatusEvent(contestId, wire, startedAt, endsAt, message);
        String destination = WebSocketUtils.getContestRoomName(contestId) + "/status";
        broadcastBridge.send(destination, event);
        log.info("Contest {} status changed to: {}", contestId, wire);
    }

    private static ContestStatusEvent.ContestStatus toWireStatus(String statusName) {
        if (statusName == null) {
            return null;
        }
        return switch (statusName) {
            case "RUNNING" -> ContestStatusEvent.ContestStatus.RUNNING;
            case "FINISHED" -> ContestStatusEvent.ContestStatus.ENDED;
            default -> null;
        };
    }
}

package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.notification.port.NotificationPushPort;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.app.api.dto.NotificationPayload;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link NotificationPushPort}.
 *
 * <p>Post-Candidate-4: this adapter now owns the {@code SimpMessagingTemplate}
 * call directly. The old {@code RealtimeService} god service has been
 * collapsed — its responsibilities have moved to the per-port adapters and
 * to {@code WebSocketContestRankingFlusher} (the only remaining
 * producer-side component, which owns the throttling logic the simple
 * push methods do not need).
 *
 * @author ulticode
 */
@Component
public class WebSocketNotificationPushAdapter implements NotificationPushPort {

    private final WebSocketBroadcastBridge broadcastBridge;

    public WebSocketNotificationPushAdapter(WebSocketBroadcastBridge broadcastBridge) {
        this.broadcastBridge = broadcastBridge;
    }

    @Override
    public void pushToUser(String userId, NotificationPayload payload) {
        broadcastBridge.sendToUser(
                userId, WebSocketConstants.USER_QUEUE_NOTIFICATION, payload);
    }
}
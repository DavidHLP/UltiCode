package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter of {@link BadgePushPort}. See
 * {@link WebSocketNotificationPushAdapter} for the post-Candidate-4
 * rationale on direct {@code SimpMessagingTemplate} use.
 *
 * @author ulticode
 */
@Component
public class WebSocketBadgePushAdapter implements BadgePushPort {

    private final WebSocketBroadcastBridge broadcastBridge;

    public WebSocketBadgePushAdapter(WebSocketBroadcastBridge broadcastBridge) {
        this.broadcastBridge = broadcastBridge;
    }

    @Override
    public void pushBadgeEarned(String userId, BadgeEarnedPayload payload) {
        broadcastBridge.sendToUser(
                userId, WebSocketConstants.USER_QUEUE_NOTIFICATION, payload);
    }
}
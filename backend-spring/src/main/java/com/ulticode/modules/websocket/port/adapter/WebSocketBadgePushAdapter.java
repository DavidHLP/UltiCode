package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBadgePushAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void pushBadgeEarned(String userId, BadgeEarnedPayload payload) {
        messagingTemplate.convertAndSendToUser(
                userId, WebSocketConstants.USER_QUEUE_NOTIFICATION, payload);
    }
}
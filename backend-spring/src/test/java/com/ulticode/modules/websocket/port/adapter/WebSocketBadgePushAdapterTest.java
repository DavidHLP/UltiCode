package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Post-Candidate-4: direct {@link SimpMessagingTemplate} call.
 * Sister adapter to {@link WebSocketNotificationPushAdapter}; same
 * destination, different payload type.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketBadgePushAdapter")
class WebSocketBadgePushAdapterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketBadgePushAdapter adapter;

    @Test
    @DisplayName("pushBadgeEarned sends to /queue/notification with the badge payload")
    void pushBadgeEarned_sendsToUserQueueNotification() {
        BadgeEarnedPayload payload = BadgeEarnedPayload.bronze(
                "b-1", "First Solve", "Solved your first problem", "/icons/first.png", "u-1");
        adapter.pushBadgeEarned("u-1", payload);
        verify(messagingTemplate).convertAndSendToUser(
                "u-1", WebSocketConstants.USER_QUEUE_NOTIFICATION, payload);
        verifyNoMoreInteractions(messagingTemplate);
    }
}
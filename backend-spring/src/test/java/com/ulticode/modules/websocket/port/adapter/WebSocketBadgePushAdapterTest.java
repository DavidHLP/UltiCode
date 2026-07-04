package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link WebSocketBadgePushAdapter}.
 *
 * <p>The adapter's only contract is to delegate {@code pushBadgeEarned} to
 * {@code RealtimeService.sendNotification} with the same arguments. Both
 * achievement-module call-sites ({@code AchievementTriggerServiceImpl} and
 * the legacy branch of {@code AchievementNotificationListener}) now depend
 * on this single seam — this test pins that the two paths cannot drift
 * from the same transport contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketBadgePushAdapter")
class WebSocketBadgePushAdapterTest {

    @Mock
    private RealtimeService realtimeService;

    @InjectMocks
    private WebSocketBadgePushAdapter adapter;

    @Test
    @DisplayName("pushBadgeEarned delegates to RealtimeService.sendNotification with same userId + payload")
    void pushBadgeEarned_delegatesToRealtimeService() {
        BadgeEarnedPayload payload = BadgeEarnedPayload.bronze(
                "b-1", "First Solve", "Solved your first problem", "/icons/first.png", "u-1");
        String userId = "u-1";

        adapter.pushBadgeEarned(userId, payload);

        verify(realtimeService).sendNotification(userId, payload);
        verifyNoMoreInteractions(realtimeService);
    }
}
package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.notification.dto.NotificationVO;
import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private BadgePushPort badgePushPort;

    @Mock
    private com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;

    @Mock
    private com.ulticode.common.config.FeatureFlagsProperties featureFlags;

    private AchievementNotificationListener listener;

    @BeforeEach
    void setUp() {
        // ADR-004 M4c: feature flag defaults to false → legacy path is
        // active (Q20 + manual WS push). Tests assert the legacy wiring.
        // lenient() to satisfy Mockito 5's strict stubbing rules for tests
        // that do not exercise the flag.
        lenient().when(featureFlags.isUseNotificationIntent()).thenReturn(false);
        listener = new AchievementNotificationListener(
                notificationService, notificationDispatchService, badgePushPort,
                notificationDispatcher, featureFlags);
    }

    @Test
    @DisplayName("onAchievementEarned dispatches notification AND pushes WebSocket (Q20 wiring)")
    void onAchievementEarned_dispatchesNotificationAndPushesWebSocket() {
        AchievementEarnedEvent event = AchievementEarnedEvent.of(
            "user-123", "ach-001", "badge-first-solve", "First Solve",
            "Solved your first problem", null, 3, 100
        );

        NotificationVO mockVO = new NotificationVO();
        mockVO.setId("notif-abc");
        when(notificationDispatchService.dispatch(
            eq("user-123"), eq("achievement"), eq("badge_earned"),
            contains("First Solve"), anyString(), eq("/achievements"), isNull(), eq(false)
        )).thenReturn(Optional.of(mockVO));

        listener.onAchievementEarned(event);

        verify(notificationDispatchService).dispatch(
            eq("user-123"), eq("achievement"), eq("badge_earned"),
            contains("First Solve"), anyString(), eq("/achievements"), isNull(), eq(false)
        );
        // Direct createNotification is no longer used.
        verifyNoInteractions(notificationService);

        ArgumentCaptor<BadgeEarnedPayload> payloadCaptor = ArgumentCaptor.forClass(BadgeEarnedPayload.class);
        verify(badgePushPort).pushBadgeEarned(eq("user-123"), payloadCaptor.capture());

        BadgeEarnedPayload payload = payloadCaptor.getValue();
        assertThat(payload.event()).isEqualTo("badge_earned");
        assertThat(payload.badgeId()).isEqualTo("badge-first-solve");
        assertThat(payload.badgeName()).isEqualTo("First Solve");
        assertThat(payload.badgeDescription()).isEqualTo("Solved your first problem");
        assertThat(payload.userId()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("onAchievementEarned handles WebSocket failure gracefully")
    void onAchievementEarned_handlesWebSocketFailure() {
        AchievementEarnedEvent event = AchievementEarnedEvent.of(
            "user-456", "ach-002", "badge-daily", "Daily Streak",
            "7 day streak", null, 2, 50
        );

        NotificationVO mockVO = new NotificationVO();
        mockVO.setId("notif-def");
        when(notificationDispatchService.dispatch(any(), any(), any(), any(), any(), any(), isNull(), anyBoolean()))
            .thenReturn(Optional.of(mockVO));
        doThrow(new RuntimeException("WS unavailable")).when(badgePushPort).pushBadgeEarned(any(), any());

        listener.onAchievementEarned(event);

        verify(notificationDispatchService).dispatch(any(), any(), any(), any(), any(), any(), isNull(), anyBoolean());
        verify(badgePushPort).pushBadgeEarned(any(), any());
    }
}

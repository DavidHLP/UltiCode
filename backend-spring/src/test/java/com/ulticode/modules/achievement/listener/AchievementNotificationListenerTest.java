package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.notification.dto.NotificationVO;
import com.ulticode.modules.websocket.service.RealtimeService;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AchievementNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private RealtimeService realtimeService;

    private AchievementNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new AchievementNotificationListener(notificationService, realtimeService);
    }

    @Test
    @DisplayName("onAchievementEarned creates DB notification AND pushes WebSocket")
    void onAchievementEarned_createsNotificationAndPushesWebSocket() {
        AchievementEarnedEvent event = AchievementEarnedEvent.of(
            "user-123", "ach-001", "badge-first-solve", "First Solve",
            "Solved your first problem", null, 3, 100
        );

        NotificationVO mockVO = new NotificationVO();
        mockVO.setId("notif-abc");
        when(notificationService.createNotification(
            eq("user-123"), eq("achievement"), eq("badge_earned"),
            contains("First Solve"), anyString(), eq("/achievements"), any(Map.class)
        )).thenReturn(mockVO);

        listener.onAchievementEarned(event);

        verify(notificationService).createNotification(
            eq("user-123"), eq("achievement"), eq("badge_earned"),
            contains("First Solve"), anyString(), eq("/achievements"), any(Map.class)
        );

        ArgumentCaptor<BadgeEarnedPayload> payloadCaptor = ArgumentCaptor.forClass(BadgeEarnedPayload.class);
        verify(realtimeService).sendNotification(eq("user-123"), payloadCaptor.capture());

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
        when(notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(Map.class)))
            .thenReturn(mockVO);
        doThrow(new RuntimeException("WS unavailable")).when(realtimeService).sendNotification(any(), any());

        listener.onAchievementEarned(event);

        verify(notificationService).createNotification(any(), any(), any(), any(), any(), any(), any(Map.class));
        verify(realtimeService).sendNotification(any(), any());
    }
}

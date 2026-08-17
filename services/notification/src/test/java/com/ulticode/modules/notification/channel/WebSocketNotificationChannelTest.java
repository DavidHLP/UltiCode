package com.ulticode.modules.notification.channel;

import com.ulticode.notification.api.dto.BadgeEarnedPayload;
import com.ulticode.notification.api.dto.NotificationPayload;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.notification.websocket.NotificationBroadcastPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationChannelTest {

    @Mock
    private NotificationBroadcastPort broadcastPort;

    @Test
    void publishesGenericNotificationThroughRedisSeam() {
        WebSocketNotificationChannel channel = new WebSocketNotificationChannel(broadcastPort);
        channel.send(new SubmissionCompletedIntent(
                "user-1", "submission-1", 1L, SubmissionStatus.ACCEPTED,
                "problem-1", "Problem", 12L, 34L, null, null,
                NotificationCategory.SYSTEM));

        ArgumentCaptor<NotificationPayload> payload = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(broadcastPort).sendToUser(eq("user-1"), payload.capture());
        assertThat(payload.getValue().event()).isEqualTo("notification");
        assertThat(payload.getValue().data()).containsEntry("submissionId", "submission-1");
    }

    @Test
    void publishesTypedBadgeThroughRedisSeam() {
        WebSocketNotificationChannel channel = new WebSocketNotificationChannel(broadcastPort);
        channel.send(new AchievementEarnedIntent(
                "user-1", "achievement-1", "badge-key", "Badge", "Description", "/badge.svg",
                3, 10, Instant.parse("2026-08-15T00:00:00Z"), NotificationCategory.SYSTEM));

        ArgumentCaptor<BadgeEarnedPayload> payload = ArgumentCaptor.forClass(BadgeEarnedPayload.class);
        verify(broadcastPort).sendBadgeToUser(eq("user-1"), payload.capture());
        assertThat(payload.getValue().badgeId()).isEqualTo("badge-key");
        assertThat(payload.getValue().badgeTier()).isEqualTo("gold");
    }
}

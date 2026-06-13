package com.ulticode.modules.notification.channel;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationChannelTest {

    @Mock private RealtimeService realtimeService;

    @Test
    void channelIdIsWebsocket() {
        assertThat(new WebSocketNotificationChannel(realtimeService).channelId()).isEqualTo("websocket");
    }

    @Test
    void achievementEmitsBadgeEarnedPayload() {
        WebSocketNotificationChannel ch = new WebSocketNotificationChannel(realtimeService);
        AchievementEarnedIntent intent = new AchievementEarnedIntent(
                "user-1", "ach-1", "badge-key", "Badge Name", "desc",
                "icon.png", 3, 100, java.time.Instant.now(), NotificationCategory.SYSTEM);

        ch.send(intent);

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(realtimeService).sendNotification(org.mockito.ArgumentMatchers.eq("user-1"), cap.capture());
        assertThat(cap.getValue()).isInstanceOf(BadgeEarnedPayload.class);
        BadgeEarnedPayload payload = (BadgeEarnedPayload) cap.getValue();
        assertThat(payload.badgeId()).isEqualTo("badge-key");
        assertThat(payload.badgeName()).isEqualTo("Badge Name");
        assertThat(payload.badgeTier()).isEqualTo("gold");
    }

    @Test
    void submissionEmitsNotificationPayloadWithIsAccepted() {
        WebSocketNotificationChannel ch = new WebSocketNotificationChannel(realtimeService);
        SubmissionCompletedIntent intent = new SubmissionCompletedIntent(
                "user-1", "sub-1", 1L, SubmissionStatus.ACCEPTED,
                "p-1", "Title", 100, 1024, null, null, NotificationCategory.SYSTEM);

        ch.send(intent);

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(realtimeService).sendNotification(org.mockito.ArgumentMatchers.eq("user-1"), cap.capture());
        assertThat(cap.getValue()).isInstanceOf(NotificationPayload.class);
        NotificationPayload payload = (NotificationPayload) cap.getValue();
        // ADR-004 M4d-1 finding #2: WS type string kept UPPERCASE to match
        // the legacy wire contract (frontend branches on payload.type).
        assertThat(payload.type()).isEqualTo("SUBMISSION");
        assertThat(payload.data()).containsEntry("isAccepted", true);
        assertThat(payload.data()).containsEntry("submissionId", "sub-1");
    }

    @Test
    void contestStartingEmitsContestReminderType() {
        WebSocketNotificationChannel ch = new WebSocketNotificationChannel(realtimeService);
        ContestStartingIntent intent = new ContestStartingIntent(
                "user-1", "c-1", "Title", LocalDateTime.now(), "24h",
                NotificationCategory.SYSTEM);

        ch.send(intent);

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(realtimeService).sendNotification(org.mockito.ArgumentMatchers.eq("user-1"), cap.capture());
        NotificationPayload payload = (NotificationPayload) cap.getValue();
        // ADR-004 M4d-1 finding #2: type kept UPPERCASE.
        assertThat(payload.type()).isEqualTo("CONTEST_REMINDER");
        assertThat(payload.data()).containsEntry("reminderType", "24h");
    }
}

package com.ulticode.modules.notification.channel;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InAppNotificationChannelTest {

    @Mock private NotificationService notificationService;

    @Test
    void channelIdIsInApp() {
        assertThat(new InAppNotificationChannel(notificationService).channelId()).isEqualTo("in_app");
    }

    @Test
    void supportsAlwaysTrue() {
        InAppNotificationChannel ch = new InAppNotificationChannel(notificationService);
        assertThat(ch.supports(sampleSubmission())).isTrue();
        assertThat(ch.supports(sampleAchievement())).isTrue();
        assertThat(ch.supports(sampleFollow())).isTrue();
    }

    @Test
    void sendSubmitsRowViaCreateNotificationRowOnly() {
        when(notificationService.createNotificationRowOnly(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

        InAppNotificationChannel ch = new InAppNotificationChannel(notificationService);
        ch.send(sampleAchievement());

        ArgumentCaptor<String> typeCap = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createNotificationRowOnly(
                eq("user-1"), typeCap.capture(), eq("SYSTEM"),
                anyString(), anyString(), anyString(), any());
        // Type name matches the record's simpleName so the frontends can
        // filter on the notification type column.
        assertThat(typeCap.getValue()).isEqualTo("AchievementEarnedIntent");
    }

    @Test
    void submissionMetadataIncludesIsAccepted() {
        when(notificationService.createNotificationRowOnly(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

        InAppNotificationChannel ch = new InAppNotificationChannel(notificationService);
        ch.send(sampleSubmission());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> metaCap =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(notificationService).createNotificationRowOnly(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), metaCap.capture());
        assertThat(metaCap.getValue()).containsEntry("isAccepted", true);
        assertThat(metaCap.getValue()).containsEntry("submissionId", "sub-1");
    }

    @Test
    void followRendersLinkAndTitle() {
        when(notificationService.createNotificationRowOnly(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(null);

        InAppNotificationChannel ch = new InAppNotificationChannel(notificationService);
        ch.send(sampleFollow());

        verify(notificationService).createNotificationRowOnly(
                eq("target-1"), eq("FollowReceivedIntent"), eq("COMMUNICATION"),
                eq("alice followed you"), eq(""), eq("/profile/alice"), anyMap());
    }

    // --- Sample intent factories ---

    private static SubmissionCompletedIntent sampleSubmission() {
        return new SubmissionCompletedIntent(
                "user-1", "sub-1", 1L, SubmissionStatus.ACCEPTED,
                "p-1", "Title", 100, 1024, null, null, NotificationCategory.SYSTEM);
    }

    private static AchievementEarnedIntent sampleAchievement() {
        return new AchievementEarnedIntent(
                "user-1", "ach-1", "key", "Name", "desc", null, 1, 10,
                NotificationCategory.SYSTEM);
    }

    private static FollowReceivedIntent sampleFollow() {
        com.ulticode.modules.user.entity.User u = new com.ulticode.modules.user.entity.User();
        u.setId("follower-1");
        u.setUsername("alice");
        return new FollowReceivedIntent("target-1", "follower-1", "alice", NotificationCategory.COMMUNICATION);
    }
}

package com.ulticode.modules.notification.channel;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.notification.websocket.NotificationBroadcastPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationChannelContractTest {

    @Test
    void notificationOwnsExactlyTheThreeDeliveryChannels() {
        List<NotificationChannel> channels = List.of(
                new InAppNotificationChannel(mock(NotificationService.class)),
                new EmailNotificationChannel(mock(com.ulticode.modules.email.service.EmailService.class)),
                new WebSocketNotificationChannel(mock(NotificationBroadcastPort.class)));

        assertThat(channels).extracting(NotificationChannel::channelId)
                .containsExactlyInAnyOrder("in_app", "email", "websocket");
        NotificationIntent intent = new SubmissionCompletedIntent(
                "user-1", "submission-1", 1L, SubmissionStatus.ACCEPTED,
                "problem-1", "Problem", 1L, 2L, null, null,
                NotificationCategory.SYSTEM);
        assertThat(channels).allSatisfy(channel -> assertThat(channel.supports(intent)).isTrue());
    }
}

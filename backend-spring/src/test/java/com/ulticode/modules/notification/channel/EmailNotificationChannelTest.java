package com.ulticode.modules.notification.channel;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.email.dto.SendEmailDTO;
import com.ulticode.modules.email.service.EmailService;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationChannelTest {

    @Mock private EmailService emailService;
    @Mock private UserMapper userMapper;

    @Test
    void channelIdIsEmail() {
        assertThat(new EmailNotificationChannel(emailService).channelId()).isEqualTo("email");
    }

    @Test
    void supportsMatrix() {
        EmailNotificationChannel ch = new EmailNotificationChannel(emailService);
        assertThat(ch.supports(sampleSubmission())).isTrue();
        assertThat(ch.supports(sampleAchievement())).isTrue();
        assertThat(ch.supports(sampleFollow())).isFalse();
    }

    @Test
    void supportsRejectsInFlightSubmission() {
        EmailNotificationChannel ch = new EmailNotificationChannel(emailService);
        SubmissionCompletedIntent pending = new SubmissionCompletedIntent(
                "user-1", "sub-1", 1L, SubmissionStatus.PENDING,
                "p-1", "Title", 0, 0, null, null, NotificationCategory.SYSTEM);
        SubmissionCompletedIntent judging = new SubmissionCompletedIntent(
                "user-1", "sub-2", 1L, SubmissionStatus.JUDGING,
                "p-1", "Title", 0, 0, null, null, NotificationCategory.SYSTEM);
        assertThat(ch.supports(pending)).isFalse();
        assertThat(ch.supports(judging)).isFalse();
    }

    @Test
    void sendThrowsBusinessExceptionWhenUserHasNoEmail() {
        EmailNotificationChannel ch = new EmailNotificationChannel(emailService);
        ch.userMapper = userMapper;
        when(userMapper.selectById("user-1")).thenReturn(null);

        assertThatThrownBy(() -> ch.send(sampleSubmission()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("user-1");
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void sendCallsEmailServiceWithRecipient() {
        EmailNotificationChannel ch = new EmailNotificationChannel(emailService);
        ch.userMapper = userMapper;
        User u = new User();
        u.setId("user-1");
        u.setEmail("user@example.com");
        when(userMapper.selectById("user-1")).thenReturn(u);

        ch.send(sampleAchievement());

        ArgumentCaptor<SendEmailDTO> cap = ArgumentCaptor.forClass(SendEmailDTO.class);
        verify(emailService).sendEmail(cap.capture());
        assertThat(cap.getValue().getTo()).isEqualTo("user@example.com");
        assertThat(cap.getValue().getTemplateId()).isEqualTo("notification.achievement.earned");
    }

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
        User u = new User();
        u.setId("follower-1");
        u.setUsername("alice");
        return FollowReceivedIntent.of(u, "user-1");
    }
}

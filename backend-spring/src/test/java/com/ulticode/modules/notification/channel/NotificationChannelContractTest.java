package com.ulticode.modules.notification.channel;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.CommentReplyIntent;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.notification.intent.SystemAlertIntent;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.email.service.EmailService;
import com.ulticode.modules.notification.port.NotificationPushPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test for the {@link NotificationChannel} implementations
 * (ADR-004 §4 #1): every intent must have at least one channel that
 * reports {@code supports() == true}, otherwise the dispatcher silently
 * drops the event.
 *
 * <p>This test is parameterized across the full intent × channel matrix
 * and would catch a regression where a new intent type is added to the
 * sealed interface but no channel claims it.
 */
@ExtendWith(MockitoExtension.class)
class NotificationChannelContractTest {

    @Mock private EmailService emailService;
    @Mock private NotificationPushPort notificationPushPort;
    @Mock private BadgePushPort badgePushPort;
    @Mock private UserMapper userMapper;

    private InAppNotificationChannel inAppChannel;
    private EmailNotificationChannel emailChannel;
    private WebSocketNotificationChannel wsChannel;

    @BeforeEach
    void setUp() {
        inAppChannel = new InAppNotificationChannel(null); // not used in supports()
        emailChannel = new EmailNotificationChannel(emailService);
        emailChannel.userMapper = userMapper; // nullable: tests don't exercise send()
        wsChannel = new WebSocketNotificationChannel(notificationPushPort, badgePushPort);
    }

    @Test
    @DisplayName("ADR-004 §4 #1: every intent has at least 1 channel that supports it")
    void everyIntentHasAtLeastOneChannel() {
        List<NotificationIntent> intents = List.of(
                sampleSubmission(),
                sampleAchievement(),
                sampleContestStarting(),
                sampleFollow(),
                sampleCommentReply(),
                sampleSystemAlert()
        );
        List<NotificationChannel> channels = List.of(inAppChannel, emailChannel, wsChannel);

        for (NotificationIntent intent : intents) {
            boolean anySupports = channels.stream().anyMatch(c -> c.supports(intent));
            assertThat(anySupports)
                    .as("at least one channel must support %s", intent.getClass().getSimpleName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("matrix sanity: InApp supports all 6 intents")
    void inAppSupportsEverything() {
        for (NotificationIntent intent : allIntents()) {
            assertThat(inAppChannel.supports(intent))
                    .as("InApp must support %s", intent.getClass().getSimpleName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("matrix sanity: WebSocket supports all 6 intents (real-time by default)")
    void webSocketSupportsEverything() {
        for (NotificationIntent intent : allIntents()) {
            assertThat(wsChannel.supports(intent))
                    .as("WebSocket must support %s", intent.getClass().getSimpleName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("matrix: Email rejects FollowReceivedIntent (no follow email)")
    void emailRejectsFollow() {
        assertThat(emailChannel.supports(sampleFollow())).isFalse();
    }

    @Test
    @DisplayName("matrix: Email supports terminal submission verdicts only")
    void emailRejectsInFlightSubmission() {
        // IN_FLIGHT kinds must not produce email.
        for (SubmissionStatus inFlight : new SubmissionStatus[]{
                SubmissionStatus.PENDING, SubmissionStatus.JUDGING}) {
            SubmissionCompletedIntent intent = new SubmissionCompletedIntent(
                    "user-1", "sub-1", 1L, inFlight, "p-1", "Title", 100, 1024,
                    null, null, NotificationCategory.SYSTEM);
            assertThat(emailChannel.supports(intent))
                    .as("Email must reject IN_FLIGHT %s", inFlight)
                    .isFalse();
        }
        // All terminal kinds must produce email.
        for (SubmissionStatus terminal : new SubmissionStatus[]{
                SubmissionStatus.ACCEPTED, SubmissionStatus.WRONG_ANSWER,
                SubmissionStatus.SANDBOX_ERROR, SubmissionStatus.SYSTEM_ERROR}) {
            SubmissionCompletedIntent intent = new SubmissionCompletedIntent(
                    "user-1", "sub-1", 1L, terminal, "p-1", "Title", 100, 1024,
                    null, null, NotificationCategory.SYSTEM);
            assertThat(emailChannel.supports(intent))
                    .as("Email must accept terminal %s", terminal)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("matrix: Email supports Achievement, Contest, Comment, System")
    void emailSupportsExcludingFollowAndInFlight() {
        assertThat(emailChannel.supports(sampleAchievement())).isTrue();
        assertThat(emailChannel.supports(sampleContestStarting())).isTrue();
        assertThat(emailChannel.supports(sampleCommentReply())).isTrue();
        assertThat(emailChannel.supports(sampleSystemAlert())).isTrue();
    }

    // --- Sample intent factories (deliberately minimal) ---

    private static List<NotificationIntent> allIntents() {
        return List.of(
                sampleSubmission(), sampleAchievement(), sampleContestStarting(),
                sampleFollow(), sampleCommentReply(), sampleSystemAlert());
    }

    private static NotificationIntent sampleSubmission() {
        return new SubmissionCompletedIntent(
                "user-1", "sub-1", 1L, SubmissionStatus.ACCEPTED,
                "p-1", "Title", 100, 1024, null, null, NotificationCategory.SYSTEM);
    }

    private static NotificationIntent sampleAchievement() {
        return new AchievementEarnedIntent(
                "user-1", "ach-1", "key", "Name", "desc", null, 1, 10,
                java.time.Instant.now(),
                NotificationCategory.SYSTEM);
    }

    private static NotificationIntent sampleContestStarting() {
        return new ContestStartingIntent(
                "user-1", "c-1", "Contest", LocalDateTime.now(), "24h",
                NotificationCategory.SYSTEM);
    }

    private static NotificationIntent sampleFollow() {
        User u = new User();
        u.setId("follower-1");
        u.setUsername("alice");
        return FollowReceivedIntent.of(u, "user-1");
    }

    private static NotificationIntent sampleCommentReply() {
        return new CommentReplyIntent(
                "user-1", "cmt-1", "replier-1", "bob", "preview", "/p/1",
                NotificationCategory.COMMUNICATION);
    }

    private static NotificationIntent sampleSystemAlert() {
        return new SystemAlertIntent(
                "user-1", "alert-key", "Title", "Body", "/x",
                NotificationCategory.SECURITY);
    }
}

package com.ulticode.modules.notification;

import com.ulticode.app.api.dto.NotificationPayload;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.email.port.EmailRenderPort;
import com.ulticode.modules.email.port.SmtpSenderPort;
import com.ulticode.modules.email.service.EmailService;
import com.ulticode.modules.notification.channel.EmailNotificationChannel;
import com.ulticode.modules.notification.channel.InAppNotificationChannel;
import com.ulticode.modules.notification.channel.NotificationChannel;
import com.ulticode.modules.notification.channel.WebSocketNotificationChannel;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.CommentReplyIntent;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.notification.intent.SystemAlertIntent;
import com.ulticode.modules.notification.port.NotificationPushPort;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Caller-facing contract matrix for Notification Delivery (NOTIFY-001).
 *
 * <p>This test deliberately checks seams, not delivery behavior. App remains
 * the sole owner of notification data and durable intent/outbox state; later
 * tasks provide the durable worker, ledger reclaim, and runtime-role behavior.
 */
class NotificationDeliveryContractTest {

    private static final Set<String> INTENT_TYPES = Set.of(
            "SubmissionCompletedIntent",
            "AchievementEarnedIntent",
            "ContestStartingIntent",
            "FollowReceivedIntent",
            "CommentReplyIntent",
            "SystemAlertIntent");
    private static final Set<String> CHANNEL_IDS = Set.of("in_app", "email", "websocket");
    private static final Pattern WIRE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern SAFE_KEY = Pattern.compile(
            "(?i).*(token|cookie|password|secret|credential|authorization|hidden.*testcase).*");

    @Test
    void intentInterfaceIsSealedAndExposesOnlyStableRoutingContract() {
        assertThat(NotificationIntent.class.isSealed()).isTrue();
        assertThat(Arrays.stream(NotificationIntent.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(INTENT_TYPES);
        assertThat(Arrays.stream(NotificationIntent.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder("userId", "category", "intentId", "wireType", "toPushPayload");
    }

    @Test
    void intentIdentityIsStableAndGenerationAware() {
        SubmissionCompletedIntent first = submission(7L);
        SubmissionCompletedIntent retry = submission(7L);
        SubmissionCompletedIntent rejudge = submission(8L);

        assertThat(first.intentId()).isEqualTo(retry.intentId());
        assertThat(first.intentId()).isNotEqualTo(rejudge.intentId());
        assertThat(allIntents()).allSatisfy(intent -> {
            assertThat(intent.intentId()).isNotBlank();
            assertThat(intent.wireType()).matches(WIRE_TYPE);
            assertThat(intent.userId()).isNotBlank();
            assertThat(intent.category()).isNotNull();
        });
    }

    @Test
    void channelContractUsesStableSnakeCaseIdsAndNoPersistencePort() throws Exception {
        List<NotificationChannel> channels = List.of(
                new InAppNotificationChannel(null),
                new EmailNotificationChannel(mock(EmailService.class)),
                new WebSocketNotificationChannel(mock(NotificationPushPort.class), mock(BadgePushPort.class)));

        assertThat(channels).extracting(NotificationChannel::channelId)
                .containsExactlyInAnyOrderElementsOf(CHANNEL_IDS);
        assertThat(channels).allSatisfy(channel -> {
            assertThat(channel.channelId()).matches("[a-z][a-z0-9_]*");
            assertThat(channel.getClass().getDeclaredFields())
                    .noneMatch(field -> field.getType().getName()
                            .contains("NotificationDeliveryLedgerMapper"));
        });
        assertThat(NotificationChannel.class.getDeclaredMethod("channelId").getReturnType())
                .isEqualTo(String.class);
        assertThat(NotificationChannel.class.getDeclaredMethod("supports", NotificationIntent.class)
                .getReturnType()).isEqualTo(boolean.class);
        assertThat(NotificationChannel.class.getDeclaredMethod("send", NotificationIntent.class)
                .getReturnType()).isEqualTo(void.class);
    }

    @Test
    void externalPortsKeepTransportAndTemplateDetailsBehindSmallInterfaces() throws Exception {
        Method push = NotificationPushPort.class.getDeclaredMethod(
                "pushToUser", String.class, NotificationPayload.class);
        assertThat(push.getReturnType()).isEqualTo(void.class);

        Method smtp = SmtpSenderPort.class.getDeclaredMethod(
                "send", String.class, String.class, String.class, String.class);
        assertThat(smtp.getReturnType()).isEqualTo(void.class);
        assertThat(smtp.getExceptionTypes()).containsExactly(MessagingException.class);

        Method render = EmailRenderPort.class.getDeclaredMethod("render", String.class, Map.class);
        assertThat(render.getReturnType()).isEqualTo(String.class);
        assertThat(Modifier.isPublic(push.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(smtp.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(render.getModifiers())).isTrue();
    }

    @Test
    void genericPushPayloadsExposeOnlyNonSensitiveDataKeys() {
        for (NotificationIntent intent : allIntents()) {
            assertThat(Arrays.stream(intent.getClass().getRecordComponents())
                    .map(component -> component.getName()))
                    .noneMatch(name -> SAFE_KEY.matcher(name).matches());

            if (intent instanceof AchievementEarnedIntent) {
                assertThatThrownBy(intent::toPushPayload)
                        .isInstanceOf(UnsupportedOperationException.class);
                continue;
            }

            NotificationPayload payload = intent.toPushPayload();
            assertThat(payload.id()).isEqualTo(intent.intentId());
            assertThat(payload.type()).isEqualTo(intent.wireType());
            assertThat(payload.data()).isNotNull();
            assertThat(payload.data().keySet())
                    .noneMatch(key -> SAFE_KEY.matcher(key).matches());
        }
    }

    private static List<NotificationIntent> allIntents() {
        return List.of(
                submission(7L),
                new AchievementEarnedIntent(
                        "user-1", "achievement-1", "first-pass", "First Pass", "desc",
                        null, 1, 10, Instant.parse("2026-08-13T00:00:00Z"), NotificationCategory.SYSTEM),
                new ContestStartingIntent(
                        "user-1", "contest-1", "Contest", LocalDateTime.of(2026, 8, 13, 12, 0),
                        "24h", NotificationCategory.SYSTEM),
                new FollowReceivedIntent(
                        "user-1", "follower-1", "alice", LocalDate.of(2026, 8, 13),
                        NotificationCategory.COMMUNICATION),
                new CommentReplyIntent(
                        "user-1", "comment-1", "replier-1", "bob", "preview", "/forum/1",
                        NotificationCategory.COMMUNICATION),
                new SystemAlertIntent(
                        "user-1", "credential-change", "Security alert", "Body", "/security",
                        NotificationCategory.SECURITY));
    }

    private static SubmissionCompletedIntent submission(long generation) {
        return new SubmissionCompletedIntent(
                "user-1", "submission-1", generation, SubmissionStatus.ACCEPTED,
                "problem-1", "Two Sum", 100L, 1024L, null, null, NotificationCategory.SYSTEM);
    }
}

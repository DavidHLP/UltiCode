package com.ulticode.modules.notification.consumer;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.notification.event.NotificationEventIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationIntentEventConsumerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private NotificationIntentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationIntentEventConsumer(notificationDispatcher);
    }

    @Test
    void reconstructsFollowIntentAndUsesDurableRetryEntryPoint() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("intentType", "FOLLOW");
        payload.put("intentId", "follow:user-1:follower-1:2026-08-13");
        payload.put("userId", "user-1");
        payload.put("category", "COMMUNICATION");
        payload.put("followerUserId", "follower-1");
        payload.put("followerUsername", "alice");
        payload.put("followDay", "2026-08-13");

        consumer.consume(
                NotificationEventIdentity.eventId(
                        (String) payload.get("intentId")),
                payload);

        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(notificationDispatcher).dispatchForDurableRetry(intent.capture());
        assertThat(intent.getValue()).isInstanceOf(FollowReceivedIntent.class);
        assertThat(intent.getValue().intentId()).isEqualTo(payload.get("intentId"));
    }

    @Test
    void rejectsIdentityMismatchBeforeDispatch() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("intentType", "SUBMISSION");
        payload.put("intentId", "submission:submission-1:g8");
        payload.put("userId", "user-1");
        payload.put("category", "SYSTEM");
        payload.put("submissionId", "submission-1");
        payload.put("generation", 7L);
        payload.put("status", "Accepted");

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification intent identity mismatch");
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void rejectsEventIdDriftBeforeDispatch() {
        Map<String, Object> payload = Map.of(
                "intentType", "FOLLOW",
                "intentId", "follow:user-1:follower-1:2026-08-13",
                "userId", "user-1",
                "category", "COMMUNICATION",
                "followerUserId", "follower-1",
                "followerUsername", "alice",
                "followDay", "2026-08-13");

        assertThatThrownBy(() -> consumer.consume("notification-drift", payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification event identity mismatch");
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void reconstructsSubmissionGenerationAndStatus() {
        Map<String, Object> payload = Map.of(
                "intentType", "SUBMISSION",
                "intentId", "submission:submission-1:g7",
                "userId", "user-1",
                "category", "SYSTEM",
                "submissionId", "submission-1",
                "generation", 7L,
                "status", "Accepted");

        NotificationIntent intent = NotificationIntentEventConsumer.toIntent(payload);

        assertThat(intent).isInstanceOfSatisfying(SubmissionCompletedIntent.class, submission -> {
            assertThat(submission.generation()).isEqualTo(7L);
            assertThat(submission.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        });
        verifyNoInteractions(notificationDispatcher);
    }
}

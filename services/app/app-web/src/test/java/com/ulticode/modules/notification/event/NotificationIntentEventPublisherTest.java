package com.ulticode.modules.notification.event;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationIntentEventPublisherTest {

    @Mock
    private IntegrationEventPublisher integrationEventPublisher;

    @Test
    void publishesStableBoundedEventIdAndGenerationAwareAggregate() {
        NotificationIntentEventPublisher publisher = new NotificationIntentEventPublisher(
                integrationEventPublisher);
        SubmissionCompletedIntent intent = new SubmissionCompletedIntent(
                "user-1", "submission-1", 7L, SubmissionStatus.ACCEPTED,
                "problem-1", "Two Sum", 100L, 1024L, null, null,
                NotificationCategory.SYSTEM);

        String eventId = publisher.publish(intent);

        assertThat(eventId).hasSize(40).startsWith("notification-");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(integrationEventPublisher).publishWithId(
                eq(eventId), eq("App"), eq(NotificationIntentEventPublisher.EVENT_TYPE),
                eq("submission-1"), eq(7L), isNull(), isNull(), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("intentId", intent.intentId())
                .containsEntry("intentType", "SUBMISSION")
                .containsEntry("generation", 7L)
                .containsEntry("status", "Accepted");
    }

    @Test
    void retriesReuseTheSameEventIdForTheSameIntent() {
        NotificationIntentEventPublisher publisher = new NotificationIntentEventPublisher(
                integrationEventPublisher);
        com.ulticode.modules.notification.intent.FollowReceivedIntent intent =
                new com.ulticode.modules.notification.intent.FollowReceivedIntent(
                        "user-1", "follower-1", "alice",
                        java.time.LocalDate.of(2026, 8, 13),
                        NotificationCategory.COMMUNICATION);

        assertThat(publisher.publish(intent)).isEqualTo(publisher.publish(intent));
    }

    @Test
    void rejectsMissingIntent() {
        NotificationIntentEventPublisher publisher = new NotificationIntentEventPublisher(
                integrationEventPublisher);

        assertThatThrownBy(() -> publisher.publish(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification intent must not be null");
    }
}

package com.ulticode.modules.notification.consumer;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedNotificationConsumerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Test
    void terminalEventPreservesIntentIdentityAndPayloadFacts() {
        SubmissionJudgedNotificationConsumer consumer =
                new SubmissionJudgedNotificationConsumer(notificationDispatcher);

        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", "42",
                "generation", 7,
                "verdict", "Accepted",
                "runtimeMs", 123,
                "memoryMb", 2.5));

        ArgumentCaptor<NotificationIntent> captor = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(notificationDispatcher).dispatchForDurableRetry(captor.capture());
        SubmissionCompletedIntent intent = (SubmissionCompletedIntent) captor.getValue();

        assertThat(intent.intentId()).isEqualTo("submission:submission-1:g7");
        assertThat(intent.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(intent.problemId()).isEqualTo("42");
        assertThat(intent.elapsedMs()).isEqualTo(123L);
        assertThat(intent.memoryBytes()).isEqualTo((long) (2.5 * 1024L * 1024L));
        assertThat(intent.toPushPayload().data())
                .containsEntry("submissionId", "submission-1")
                .containsEntry("status", "Accepted")
                .containsEntry("elapsedMs", 123L)
                .containsEntry("memoryBytes", (long) (2.5 * 1024L * 1024L));
    }

    @Test
    void nonTerminalEventIsAcknowledgableWithoutNotification() {
        SubmissionJudgedNotificationConsumer consumer =
                new SubmissionJudgedNotificationConsumer(notificationDispatcher);

        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "generation", 7,
                "verdict", "Judging"));

        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void malformedEventPropagatesSoInboxCanRetry() {
        SubmissionJudgedNotificationConsumer consumer =
                new SubmissionJudgedNotificationConsumer(notificationDispatcher);

        assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "generation", 7,
                "verdict", "not-a-status")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void dispatcherFailurePropagatesSoInboxCanRetry() {
        doThrow(new IllegalStateException("ledger unavailable"))
                .when(notificationDispatcher)
                .dispatchForDurableRetry(org.mockito.ArgumentMatchers.any());
        SubmissionJudgedNotificationConsumer consumer =
                new SubmissionJudgedNotificationConsumer(notificationDispatcher);

        assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "generation", 7,
                "verdict", "Wrong Answer")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ledger unavailable");
    }
}

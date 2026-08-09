package com.ulticode.modules.websocket.consumer;

import com.ulticode.app.api.dto.SubmissionResultPayload;
import com.ulticode.app.api.service.SubmissionResultPushPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedWebSocketConsumerTest {

    @Mock
    private SubmissionResultPushPort resultPushPort;

    @Test
    void restoresLegacySubmissionResultPayloadFromDurableEvent() {
        SubmissionJudgedWebSocketConsumer consumer = new SubmissionJudgedWebSocketConsumer(resultPushPort);

        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", "problem-1",
                "contestId", "contest-1",
                "generation", 7,
                "verdict", "Accepted",
                "runtimeMs", 42,
                "memoryMb", 1.5));

        ArgumentCaptor<SubmissionResultPayload> payload =
                ArgumentCaptor.forClass(SubmissionResultPayload.class);
        verify(resultPushPort).emitSubmissionResult(eq("user-1"), payload.capture());
        assertThat(payload.getValue().event()).isEqualTo("submission_result");
        assertThat(payload.getValue().submissionId()).isEqualTo("submission-1");
        assertThat(payload.getValue().contestId()).isEqualTo("contest-1");
        assertThat(payload.getValue().problemId()).isEqualTo("problem-1");
        assertThat(payload.getValue().status()).isEqualTo("Accepted");
        assertThat(payload.getValue().timeUsed()).isEqualTo(42);
        assertThat(payload.getValue().memoryUsed()).isEqualTo(1572864L);
    }

    @Test
    void pushFailurePropagatesForInboxRetry() {
        doThrow(new IllegalStateException("websocket unavailable"))
                .when(resultPushPort).emitSubmissionResult(eq("user-1"), org.mockito.ArgumentMatchers.any());
        SubmissionJudgedWebSocketConsumer consumer = new SubmissionJudgedWebSocketConsumer(resultPushPort);

        assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "verdict", "Wrong Answer")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("websocket unavailable");
    }
}

package com.ulticode.modules.websocket.consumer;

import com.ulticode.submission.api.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.port.SubmissionResultPushPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
                "problemId", 42,
                "contestId", "contest-1",
                "generation", 7,
                "verdict", "Accepted",
                "runtimeMs", 42.75,
                "memoryMb", 1.5));

        ArgumentCaptor<SubmissionResultPayload> payload =
                ArgumentCaptor.forClass(SubmissionResultPayload.class);
        verify(resultPushPort).emitSubmissionResult(eq("user-1"), payload.capture());
        assertThat(payload.getValue().event()).isEqualTo("submission_result");
        assertThat(payload.getValue().submissionId()).isEqualTo("submission-1");
        assertThat(payload.getValue().contestId()).isEqualTo("contest-1");
        assertThat(payload.getValue().problemId()).isEqualTo("42");
        assertThat(payload.getValue().status()).isEqualTo("Accepted");
        assertThat(payload.getValue().timeUsed()).isEqualTo(42);
        assertThat(payload.getValue().memoryUsed()).isEqualTo(1572864L);
    }

    @Test
    void pushFailurePropagatesForInboxRetry() {
        doThrow(new IllegalStateException("websocket unavailable"))
                .when(resultPushPort).emitSubmissionResult(eq("user-1"), any());
        SubmissionJudgedWebSocketConsumer consumer = new SubmissionJudgedWebSocketConsumer(resultPushPort);

        assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "verdict", "Wrong Answer",
                "runtimeMs", " ",
                "memoryMb", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("websocket unavailable");

        ArgumentCaptor<SubmissionResultPayload> payload = ArgumentCaptor.forClass(SubmissionResultPayload.class);
        verify(resultPushPort).emitSubmissionResult(eq("user-1"), payload.capture());
        assertThat(payload.getValue().timeUsed()).isZero();
        assertThat(payload.getValue().memoryUsed()).isZero();
    }

    @Test
    void nonTerminalVerdictSkipsMalformedMetricsButStillRequiresIdentity() {
        SubmissionJudgedWebSocketConsumer consumer = new SubmissionJudgedWebSocketConsumer(resultPushPort);

        consumer.consume(Map.of(
                "submissionId", "submission-1", "userId", "user-1",
                "verdict", "Judging", "runtimeMs", "invalid", "memoryMb", "invalid"));
        assertThatThrownBy(() -> consumer.consume(Map.of("submissionId", " ", "verdict", "Judging")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing SubmissionJudged field: submissionId");
        verifyNoInteractions(resultPushPort);
    }

    @Test
    void infrastructureVerdictUsesLegacyClampingAndOptionalValues() {
        SubmissionJudgedWebSocketConsumer consumer = new SubmissionJudgedWebSocketConsumer(resultPushPort);

        consumer.consume(Map.of(
                "submissionId", "submission-1", "userId", 17,
                "verdict", "Sandbox Error", "generation", "invalid",
                "problemId", "null", "runtimeMs", "2147483648", "memoryMb", "Infinity"));

        ArgumentCaptor<SubmissionResultPayload> payload = ArgumentCaptor.forClass(SubmissionResultPayload.class);
        verify(resultPushPort).emitSubmissionResult(eq("17"), payload.capture());
        assertThat(payload.getValue().status()).isEqualTo("Sandbox Error");
        assertThat(payload.getValue().problemId()).isEqualTo("null");
        assertThat(payload.getValue().contestId()).isNull();
        assertThat(payload.getValue().timeUsed()).isEqualTo(Integer.MAX_VALUE);
        assertThat(payload.getValue().memoryUsed()).isZero();
    }

    @Test
    void malformedMetricStringsOnTerminalVerdictsRemainRetryable() {
        SubmissionJudgedWebSocketConsumer consumer = new SubmissionJudgedWebSocketConsumer(resultPushPort);

        for (String field : new String[]{"runtimeMs", "memoryMb"}) {
            assertThatThrownBy(() -> consumer.consume(Map.of(
                    "submissionId", "submission-1", "userId", "user-1", "verdict", "Accepted",
                    field, "invalid")))
                    .as(field)
                    .isInstanceOf(NumberFormatException.class);
        }
        verifyNoInteractions(resultPushPort);
    }
}

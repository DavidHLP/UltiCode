package com.ulticode.modules.contest.consumer;

import com.ulticode.modules.contest.integration.ContestSubmissionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubmissionCreatedContestConsumerTest {

    @Mock
    private ContestSubmissionAdapter adapter;

    private SubmissionCreatedContestConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SubmissionCreatedContestConsumer(adapter);
    }

    @Test
    void mapsVirtualCreatedPayloadWithoutSensitiveFields() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 17, 1, 2, 3);
        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", "42",
                "contestId", "contest-1",
                "virtualSessionId", "session-1",
                "generation", 1,
                "language", "java",
                "occurredAt", occurredAt.toString()));

        verify(adapter).recordSubmissionFromEvent(
                eq("submission-1"), eq("user-1"), eq(42L), eq("contest-1"),
                eq("session-1"), eq(occurredAt));
    }

    @Test
    void passesNullVirtualSessionForRegularSubmissions() {
        consumer.consume(Map.of(
                "submissionId", "submission-2",
                "userId", "user-2",
                "problemId", 7,
                "contestId", "contest-2",
                "generation", 1L,
                "language", "python",
                "occurredAt", "2026-08-17T01:02:03"));

        verify(adapter).recordSubmissionFromEvent(
                eq("submission-2"), eq("user-2"), eq(7L), eq("contest-2"),
                isNull(), eq(LocalDateTime.of(2026, 8, 17, 1, 2, 3)));
    }

    @Test
    void rejectsMissingRequiredFields() {
        Map<String, Object> payload = Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", 42,
                "generation", 1,
                "language", "java",
                "occurredAt", "2026-08-17T01:02:03");

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing SubmissionCreated field: contestId");
    }

    @Test
    void rejectsNonPositiveGeneration() {
        assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", 42,
                "contestId", "contest-1",
                "generation", 0,
                "language", "java",
                "occurredAt", "2026-08-17T01:02:03")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid SubmissionCreated generation");
    }

    @Test
    void rejectsOverlongLanguage() {
        assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", 42,
                "contestId", "contest-1",
                "generation", 1,
                "language", "x".repeat(51),
                "occurredAt", "2026-08-17T01:02:03")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SubmissionCreated language is too long");
    }

    @Test
    void rejectsMalformedOccurredAt() {
        assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", 42,
                "contestId", "contest-1",
                "generation", 1,
                "language", "java",
                "occurredAt", "not-a-date")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid SubmissionCreated occurredAt");
    }
}

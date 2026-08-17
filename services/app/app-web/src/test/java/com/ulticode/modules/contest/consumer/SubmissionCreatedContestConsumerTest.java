package com.ulticode.modules.contest.consumer;

import com.ulticode.modules.contest.integration.ContestSubmissionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
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
}

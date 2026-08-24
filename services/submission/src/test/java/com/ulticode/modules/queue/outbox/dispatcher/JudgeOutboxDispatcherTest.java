package com.ulticode.modules.queue.outbox.dispatcher;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeQueueAdapter;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeQueue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the malformed-payload dead-letter path added by the code
 * review fix: a claimed real-dispatch row whose payload is undecodable or
 * misses required fields must be dead-lettered, never enqueued as a
 * half-null envelope and marked SENT.
 */
@DisplayName("JudgeOutboxDispatcher malformed payload handling")
class JudgeOutboxDispatcherTest {

    private JudgeOutboxMapper judgeOutboxMapper;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JudgeQueue> queueProvider = mock(ObjectProvider.class);
    private JudgeQueue judgeQueue;
    private JudgeOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        judgeOutboxMapper = mock(JudgeOutboxMapper.class);
        judgeQueue = mock(JudgeQueue.class);
        when(queueProvider.getIfAvailable()).thenReturn(judgeQueue);
        dispatcher = new JudgeOutboxDispatcher(
                judgeOutboxMapper,
                queueProvider,
                new SimpleMeterRegistry(),
                Clock.systemUTC(),
                mock(UuidGenerator.class));
    }

    private JudgeOutboxRecord row(Map<String, Object> payload) {
        JudgeOutboxRecord row = new JudgeOutboxRecord();
        row.setId("row-1");
        row.setSubmissionId("submission-1");
        row.setGeneration(3L);
        row.setPayload(payload);
        row.setIsShadow(false);
        row.setAttempts(0);
        return row;
    }

    @Test
    @DisplayName("a row with missing required fields is dead-lettered, not marked SENT")
    void missingRequiredFieldsDeadLettersRow() {
        Map<String, Object> payload = Map.of(
                "problemId", "101",
                "userId", "user-1");
                // language and code missing
        when(judgeOutboxMapper.claimRealDispatch(anyInt(), any()))
                .thenReturn(List.of(row(payload)));

        dispatcher.dispatch();

        verify(judgeOutboxMapper).markDead(eq("row-1"), anyString());
        verify(judgeOutboxMapper, never()).markSent(anyString());
        verify(judgeQueue, never()).enqueue(any());
    }

    @Test
    @DisplayName("an undecodable legacy String payload is dead-lettered")
    void undecodablePayloadDeadLettersRow() {
        when(judgeOutboxMapper.claimRealDispatch(anyInt(), any()))
                .thenReturn(List.of(row(Map.of("legacy", "not-json{"))));

        dispatcher.dispatch();

        verify(judgeOutboxMapper).markDead(eq("row-1"), anyString());
        verify(judgeOutboxMapper, never()).markSent(anyString());
    }

    @Test
    @DisplayName("a well-formed payload still dispatches and marks SENT")
    void wellFormedPayloadDispatches() {
        when(judgeOutboxMapper.claimRealDispatch(anyInt(), any()))
                .thenReturn(List.of(row(Map.of(
                        "problemId", "101",
                        "userId", "user-1",
                        "language", "java",
                        "code", "class Main {}"))));

        dispatcher.dispatch();

        verify(judgeQueue).enqueue(any(JudgeJobEnvelope.class));
        verify(judgeOutboxMapper).markSent("row-1");
        verify(judgeOutboxMapper, never()).markDead(anyString(), anyString());
    }

    @Test
    @DisplayName("envelope carries the parsed payload fields on dispatch")
    void envelopeCarriesParsedFields() {
        JudgeOutboxRecord record = row(Map.of(
                "problemId", "101",
                "userId", "user-1",
                "language", "java",
                "code", "class Main {}",
                "timeLimitMs", 1500));
        when(judgeOutboxMapper.claimRealDispatch(anyInt(), any())).thenReturn(List.of(record));

        dispatcher.dispatch();

        org.mockito.ArgumentMatcher<JudgeJobEnvelope> matches = envelope ->
                "submission-1".equals(envelope.submissionId())
                        && "101".equals(envelope.problemId())
                        && "java".equals(envelope.language())
                        && envelope.generation() == 3L
                        && envelope.timeLimitMs() == 1500;
        verify(judgeQueue).enqueue(org.mockito.ArgumentMatchers.argThat(matches));
    }
}

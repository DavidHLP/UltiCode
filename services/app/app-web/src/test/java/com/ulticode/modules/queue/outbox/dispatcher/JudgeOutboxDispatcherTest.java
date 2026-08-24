package com.ulticode.modules.queue.outbox.dispatcher;

import com.ulticode.app.api.service.JudgeEnqueuePort;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * App-local copy of the dispatcher dead-letter contract: a claimed
 * real-dispatch row whose payload is undecodable or misses required fields
 * must be dead-lettered, never enqueued as a half-null envelope and marked
 * SENT.
 */
@DisplayName("JudgeOutboxDispatcher (App) malformed payload handling")
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
                mock(UuidGenerator.class),
                mock(JudgeEnqueuePort.class));
        // Force the M3c-2 real-dispatch path for the unit test.
        ReflectionTestUtils.setField(dispatcher, "judgeQueuePortEnabled", true);
    }

    private JudgeOutboxRecord row(Map<String, Object> payload) {
        JudgeOutboxRecord record = new JudgeOutboxRecord();
        record.setId("row-1");
        record.setSubmissionId("submission-1");
        record.setGeneration(3L);
        record.setPayload(payload);
        record.setIsShadow(false);
        record.setAttempts(0);
        return record;
    }

    @Test
    @DisplayName("a row with missing required fields is dead-lettered, not marked SENT")
    void missingRequiredFieldsDeadLettersRow() {
        when(judgeOutboxMapper.claimRealDispatch(anyInt(), any()))
                .thenReturn(List.of(row(Map.of(
                        "problemId", "101",
                        "userId", "user-1"))));

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
                        "code", "class Main {}",
                        "timeLimitMs", 1500))));

        dispatcher.dispatch();

        verify(judgeQueue).enqueue(argThat((JudgeJobEnvelope envelope) ->
                "submission-1".equals(envelope.submissionId())
                        && "101".equals(envelope.problemId())
                        && "java".equals(envelope.language())
                        && envelope.generation() == 3L
                        && envelope.timeLimitMs() == 1500));
        verify(judgeOutboxMapper).markSent("row-1");
        verify(judgeOutboxMapper, never()).markDead(anyString(), anyString());
    }
}

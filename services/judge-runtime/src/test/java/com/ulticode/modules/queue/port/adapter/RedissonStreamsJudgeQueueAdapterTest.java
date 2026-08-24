package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeStreamKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.PendingEntry;
import org.redisson.api.stream.PendingResult;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.client.codec.Codec;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Redis Streams judge queue safety")
class RedissonStreamsJudgeQueueAdapterTest {

    private static final String STREAM_KEY = "judge:test:stream";
    private static final String GROUP = "judge-test-workers";
    private static final String CONSUMER = "judge-test-consumer";

    @Test
    @DisplayName("dedup marker and XADD are submitted as one Redis script")
    void enqueueUsesAtomicScript() throws Exception {
        RedissonClient redisson = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(redisson.getScript(any(Codec.class))).thenReturn(script);
        doReturn(1L).when(script).eval(
                any(RScript.Mode.class), anyString(), eq(RScript.ReturnType.LONG),
                anyList(), any(Object[].class));

        RedissonStreamsJudgeQueueAdapter adapter = adapter(redisson, 3);
        adapter.enqueue(envelope());

        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                org.mockito.ArgumentMatchers.argThat(lua -> lua.contains("SET")
                        && lua.contains("XADD")
                        && lua.contains("DEL")),
                eq(RScript.ReturnType.LONG),
                eq(List.of(JudgeStreamKeys.JUDGE_DISPATCH_SEEN_PREFIX + "submission-1:2", STREAM_KEY)),
                eq("1"), eq("5"), anyString());
    }

    @Test
    @DisplayName("an exhausted PEL entry is dead-lettered BEFORE any XCLAIM")
    void exhaustedDeliveryIsDeadLetteredWithoutClaim() {
        RedissonClient redisson = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RStream<String, String> source = mock(RStream.class);
        RScript script = mock(RScript.class);
        StreamMessageId id = new StreamMessageId(12, 0);
        PendingEntry pending = new PendingEntry(id, "old-consumer", 5_000, 3);

        when(redisson.<String, String>getStream(eq(STREAM_KEY), any(Codec.class))).thenReturn(source);
        when(redisson.getScript(any(Codec.class))).thenReturn(script);
        doReturn(1L).when(script).eval(
                any(RScript.Mode.class), anyString(), eq(RScript.ReturnType.LONG),
                anyList(), any(Object[].class));
        when(source.getPendingInfo(GROUP)).thenReturn(
                new PendingResult(1, id, id, Map.of()));
        when(source.listPending(
                eq(GROUP), eq(StreamMessageId.MIN), eq(StreamMessageId.MAX),
                eq(1_000L), eq(TimeUnit.MILLISECONDS), eq(1)))
                .thenReturn(List.of(pending));
        when(source.range(eq(1), eq(id), eq(id)))
                .thenReturn(Map.of(id, Map.of("payload", "{}")));

        RedissonStreamsJudgeQueueAdapter adapter = adapter(redisson, 3);

        Optional<JudgeJobHandle> result = adapter.claimIdle(1_000L);

        assertThat(result).isEmpty();
        // The exhausted entry must never be claimed: XCLAIM would increment
        // the broker delivery counter without a judge attempt (CR P1-4).
        verify(source, never()).claim(
                eq(GROUP), eq(CONSUMER), eq(1_000L), eq(TimeUnit.MILLISECONDS),
                any(StreamMessageId[].class));
        verify(script).eval(
                eq(RScript.Mode.READ_WRITE),
                org.mockito.ArgumentMatchers.argThat(lua -> lua.contains("XADD")
                        && lua.contains("XACK")
                        && lua.contains("SET")),
                eq(RScript.ReturnType.LONG),
                eq(List.of(STREAM_KEY, JudgeStreamKeys.JUDGE_STREAM_DLQ_KEY,
                        JudgeStreamKeys.JUDGE_STREAM_DLQ_SEEN_PREFIX + "12-0")),
                eq("{}"), eq("12-0"), eq("3"), eq(CONSUMER),
                eq("max-delivery-attempts"), eq("3600"), eq(GROUP));
        verify(source, never()).ack(GROUP, id);
    }
    @Test
    @DisplayName("poll recreates a missing consumer group and retries once")
    void pollRecreatesMissingGroupAndRetriesOnce() {
        RedissonClient redisson = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RStream<String, String> source = mock(RStream.class);
        StreamMessageId id = new StreamMessageId(13, 0);

        when(redisson.<String, String>getStream(eq(STREAM_KEY), any(Codec.class))).thenReturn(source);
        when(source.readGroup(
                eq(GROUP), eq(CONSUMER), any(org.redisson.api.stream.StreamReadGroupArgs.class)))
                .thenThrow(new IllegalStateException("NOGROUP No such key"))
                .thenReturn(Map.of(id, Map.of("payload",
                        "{\"version\":2,\"id\":\"job-1\",\"submissionId\":\"submission-1\","
                                + "\"problemId\":\"problem-1\",\"userId\":\"user-1\","
                                + "\"language\":\"java\",\"code\":\"class Main {}\","
                                + "\"timeLimitMs\":2000,\"memoryLimitKb\":262144,"
                                + "\"generation\":2,\"attemptId\":\"attempt-1\"}")));
        when(source.listGroups()).thenReturn(List.of());
        when(source.isExists()).thenReturn(true);

        RedissonStreamsJudgeQueueAdapter adapter = adapter(redisson, 3);

        Optional<JudgeJobHandle> result = adapter.poll(0);

        assertThat(result).isPresent();
        assertThat(result.get().envelope().submissionId()).isEqualTo("submission-1");
        // The recovered group must be created from 0-0 (ALL), never NEWEST:
        // a NEWEST group after NOGROUP recovery would skip every
        // already-enqueued entry whose outbox row is already SENT.
        org.mockito.ArgumentMatcher<org.redisson.api.stream.StreamCreateGroupArgs> fromBeginning =
                args -> ((org.redisson.api.stream.StreamCreateGroupParams) args)
                        .getId() == org.redisson.api.stream.StreamMessageId.ALL;
        verify(source).createGroup(org.mockito.ArgumentMatchers.argThat(fromBeginning));
    }


    @Test
    @DisplayName("an entry within the budget is claimed and returned to the worker")
    void unexhaustedEntryIsClaimed() {
        RedissonClient redisson = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RStream<String, String> source = mock(RStream.class);
        StreamMessageId id = new StreamMessageId(12, 0);
        PendingEntry pending = new PendingEntry(id, "old-consumer", 5_000, 2);

        when(redisson.<String, String>getStream(eq(STREAM_KEY), any(Codec.class))).thenReturn(source);
        when(source.getPendingInfo(GROUP)).thenReturn(
                new PendingResult(1, id, id, Map.of()));
        when(source.listPending(
                eq(GROUP), eq(StreamMessageId.MIN), eq(StreamMessageId.MAX),
                eq(1_000L), eq(TimeUnit.MILLISECONDS), eq(1)))
                .thenReturn(List.of(pending));
        when(source.claim(
                eq(GROUP), eq(CONSUMER), eq(1_000L), eq(TimeUnit.MILLISECONDS), eq(id)))
                .thenReturn(Map.of(id, Map.of("payload",
                        "{\"version\":2,\"id\":\"job-1\",\"submissionId\":\"submission-1\","
                                + "\"problemId\":\"problem-1\",\"userId\":\"user-1\","
                                + "\"language\":\"java\",\"code\":\"class Main {}\","
                                + "\"timeLimitMs\":2000,\"memoryLimitKb\":262144,"
                                + "\"generation\":2,\"attemptId\":\"attempt-1\"}")));

        RedissonStreamsJudgeQueueAdapter adapter = adapter(redisson, 3);

        Optional<JudgeJobHandle> result = adapter.claimIdle(1_000L);

        assertThat(result).isPresent();
        assertThat(result.get().envelope().submissionId()).isEqualTo("submission-1");
        verify(source).claim(
                eq(GROUP), eq(CONSUMER), eq(1_000L), eq(TimeUnit.MILLISECONDS),
                any(StreamMessageId[].class));
    }

    private RedissonStreamsJudgeQueueAdapter adapter(RedissonClient redisson,
                                                     int maxDeliveryAttempts) {
        return new RedissonStreamsJudgeQueueAdapter(
                redisson,
                new ObjectMapper(),
                STREAM_KEY,
                GROUP,
                CONSUMER,
                1_000L,
                maxDeliveryAttempts,
                null);
    }

    private JudgeJobEnvelope envelope() {
        return new JudgeJobEnvelope(
                JudgeJobEnvelope.VERSION_2,
                "job-1",
                "submission-1",
                "problem-1",
                "user-1",
                "java",
                "class Main {}",
                2_000,
                256 * 1024,
                2L,
                "attempt-1");
    }
}

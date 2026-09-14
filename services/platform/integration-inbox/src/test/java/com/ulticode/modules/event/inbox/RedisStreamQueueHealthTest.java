package com.ulticode.modules.event.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.common.metrics.WorkerSloMeters;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisStreamQueueHealthTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void observesAllQueueFieldsFromOneGroup() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streams = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streams);
        when(streams.size("dlq:test")).thenReturn(2L);
        PendingMessage pending = new PendingMessage(
                org.springframework.data.redis.connection.stream.RecordId.of("1-0"),
                Consumer.from("worker", "consumer"), Duration.ofSeconds(42), 1);
        when(streams.pending(eq("stream:test"), eq("worker"), any(Range.class), eq(1L)))
                .thenReturn(new PendingMessages("worker", List.of(pending)));
        when(redisTemplate.execute((RedisCallback<Object>) any(RedisCallback.class))).thenReturn(List.of(List.of(
                "name", "worker", "pending", "3", "lag", "7")));

        RedisStreamQueueHealth.Snapshot snapshot =
                new RedisStreamQueueHealth(redisTemplate).observe("stream:test", "worker", "dlq:test");

        assertThat(snapshot).isEqualTo(new RedisStreamQueueHealth.Snapshot(7, 3, 42, 2));
        verify(streams, never()).groups(anyString());
    }

    @Test
    void reportsUnknownWhenBrokerObservationFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForStream()).thenThrow(new IllegalStateException("redis unavailable"));

        RedisStreamQueueHealth.Snapshot snapshot =
                new RedisStreamQueueHealth(redisTemplate).observe("stream:test", "worker", null);

        assertThat(snapshot.queueLag()).isEqualTo(WorkerSloMeters.UNKNOWN);
        assertThat(snapshot.pelSize()).isEqualTo(WorkerSloMeters.UNKNOWN);
        assertThat(snapshot.oldestPendingAgeSeconds()).isEqualTo(WorkerSloMeters.UNKNOWN);
        assertThat(snapshot.dlqSize()).isEqualTo(WorkerSloMeters.UNKNOWN);
    }
}

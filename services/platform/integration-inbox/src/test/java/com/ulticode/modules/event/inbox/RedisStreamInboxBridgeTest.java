package com.ulticode.modules.event.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisStreamInboxBridgeTest {

    @SuppressWarnings("unchecked")
    @Test
    void recreatesAConsumerGroupAfterRedisReportsNogroup() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(streams.read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenThrow(new IllegalStateException("NOGROUP consumer group missing"));

        RedisStreamTransport transport = new RedisStreamTransport(
                redisTemplate, "stream:test", "Test", "consumer-1");

        assertThat(transport.ensureGroup()).isTrue();
        assertThatThrownBy(() -> transport.read(ReadOffset.lastConsumed(), 50))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOGROUP");
        assertThat(transport.ensureGroup()).isTrue();
        verify(streams, times(2)).createGroup(anyString(), any(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void stagesAndAcknowledgesOneRedisEntryEvenWhenReadPathsOverlap() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        ConsumerInboxMapper inboxMapper = mock(ConsumerInboxMapper.class);
        UuidGenerator uuidGenerator = mock(UuidGenerator.class);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId", "event-1",
                        "owner", "App",
                        "aggregateId", "aggregate-1",
                        "aggregateVersion", "1",
                        "eventType", "SubmissionJudged",
                        "schemaVersion", "1",
                        "payload", "{\"submissionId\":\"submission-1\"}"))
                .withStreamKey("stream:test")
                .withId(RecordId.of("1-0"));
        doReturn(List.of(record), List.of(record))
                .when(streams)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(uuidGenerator.newId()).thenReturn("inbox-1");
        when(inboxMapper.insertIfAbsent(
                eq("inbox-1"), eq("Test"), eq("event-1"), eq("SubmissionJudged"), anyString()))
                .thenReturn(1);

        InboxConsumer inbox = new InboxConsumer(inboxMapper, "Test", null);
        RedisStreamInboxBridge bridge = new RedisStreamInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                List.of(new RedisStreamInboxBridge.Binding(
                        "stream:test", "Test", Set.of("SubmissionJudged"), inbox,
                        (stream, eventType, owner) -> "App".equals(owner))));

        assertThat(bridge.consume()).isEqualTo(1);
        verify(inboxMapper).insertIfAbsent(
                eq("inbox-1"), eq("Test"), eq("event-1"), eq("SubmissionJudged"), anyString());
        verify(streams).acknowledge("stream:test", "Test", record.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void reclaimsPendingEntriesBeforeReadingNewStreamEntries() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        ConsumerInboxMapper inboxMapper = mock(ConsumerInboxMapper.class);
        UuidGenerator uuidGenerator = mock(UuidGenerator.class);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        MapRecord<String, String, String> record = event("event-pending", "1-0");
        PendingMessage pending = new PendingMessage(
                record.getId(), Consumer.from("Test", "old-consumer"),
                Duration.ofSeconds(60), 2);
        when(streams.pending(eq("stream:test"), eq("Test"), any(Range.class), anyLong()))
                .thenReturn(new PendingMessages("Test", List.of(pending)));
        when(streams.claim(eq("stream:test"), eq("Test"), anyString(),
                any(Duration.class), any(RecordId[].class)))
                .thenReturn(List.of(record));
        doReturn(List.of(), List.of())
                .when(streams)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(uuidGenerator.newId()).thenReturn("inbox-pending");
        when(inboxMapper.insertIfAbsent(
                eq("inbox-pending"), eq("Test"), eq("event-pending"),
                eq("SubmissionJudged"), anyString()))
                .thenReturn(1);

        InboxConsumer inbox = new InboxConsumer(inboxMapper, "Test", null);
        RedisStreamInboxBridge bridge = new RedisStreamInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                List.of(new RedisStreamInboxBridge.Binding(
                        "stream:test", "Test", Set.of("SubmissionJudged"), inbox,
                        (stream, eventType, owner) -> "App".equals(owner))));

        assertThat(bridge.consume()).isEqualTo(1);
        verify(streams).claim(eq("stream:test"), eq("Test"), anyString(),
                any(Duration.class), any(RecordId[].class));
        verify(streams).acknowledge("stream:test", "Test", record.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void poisonsMalformedPayloadAndAcknowledgesTheTransportEntry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        ConsumerInboxMapper inboxMapper = mock(ConsumerInboxMapper.class);
        UuidGenerator uuidGenerator = mock(UuidGenerator.class);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId", "event-bad",
                        "owner", "App",
                        "aggregateId", "aggregate-1",
                        "aggregateVersion", "1",
                        "eventType", "SubmissionJudged",
                        "schemaVersion", "1",
                        "payload", "not-json"))
                .withStreamKey("stream:test")
                .withId(RecordId.of("2-0"));
        doReturn(List.of(record), List.of())
                .when(streams)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(uuidGenerator.newId()).thenReturn("poison-inbox");
        when(inboxMapper.insertIfAbsent(
                eq("poison-inbox"), eq("Test"), eq("event-bad"),
                eq("IntegrationEventPoison"), anyString()))
                .thenReturn(1);

        InboxConsumer inbox = new InboxConsumer(inboxMapper, "Test", null);
        RedisStreamInboxBridge bridge = new RedisStreamInboxBridge(
                redisTemplate, inboxMapper, new ObjectMapper(), uuidGenerator,
                List.of(new RedisStreamInboxBridge.Binding(
                        "stream:test", "Test", Set.of("SubmissionJudged"), inbox,
                        (stream, eventType, owner) -> "App".equals(owner))));

        assertThat(bridge.consume()).isEqualTo(1);
        verify(inboxMapper).insertIfAbsent(
                eq("poison-inbox"), eq("Test"), eq("event-bad"),
                eq("IntegrationEventPoison"), anyString());
        verify(streams).acknowledge("stream:test", "Test", record.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void leavesTheTransportEntryPendingWhenDurableStagingFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        ConsumerInboxMapper inboxMapper = mock(ConsumerInboxMapper.class);
        UuidGenerator uuidGenerator = mock(UuidGenerator.class);
        AtomicInteger failures = new AtomicInteger();
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        MapRecord<String, String, String> record = event("event-fail", "3-0");
        doReturn(List.of(record), List.of())
                .when(streams)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(uuidGenerator.newId()).thenReturn("inbox-fail");
        when(inboxMapper.insertIfAbsent(
                eq("inbox-fail"), eq("Test"), eq("event-fail"),
                eq("SubmissionJudged"), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));

        InboxConsumer inbox = new InboxConsumer(inboxMapper, "Test", null);
        RedisStreamInboxBridge bridge = new RedisStreamInboxBridge(
                redisTemplate, inboxMapper, new ObjectMapper(), uuidGenerator,
                List.of(new RedisStreamInboxBridge.Binding(
                        "stream:test", "Test", Set.of("SubmissionJudged"), inbox,
                        (stream, eventType, owner) -> "App".equals(owner))),
                ignored -> failures.incrementAndGet());

        assertThat(bridge.consume()).isZero();
        assertThat(failures).hasValue(1);
        verify(streams, never()).acknowledge("stream:test", "Test", record.getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    void failsSoftWhenTheRedisGroupCannotBeCreatedOrFound() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        ConsumerInboxMapper inboxMapper = mock(ConsumerInboxMapper.class);
        UuidGenerator uuidGenerator = mock(UuidGenerator.class);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.createGroup(anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        InboxConsumer inbox = new InboxConsumer(inboxMapper, "Test", null);
        RedisStreamInboxBridge bridge = new RedisStreamInboxBridge(
                redisTemplate, inboxMapper, new ObjectMapper(), uuidGenerator,
                List.of(new RedisStreamInboxBridge.Binding(
                        "stream:test", "Test", Set.of("SubmissionJudged"), inbox,
                        (stream, eventType, owner) -> true)));

        assertThat(bridge.consume()).isZero();
        verify(inboxMapper, never()).insertIfAbsent(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private static MapRecord<String, String, String> event(String eventId, String recordId) {
        return StreamRecords.mapBacked(Map.of(
                        "eventId", eventId,
                        "owner", "App",
                        "aggregateId", "aggregate-1",
                        "aggregateVersion", "1",
                        "eventType", "SubmissionJudged",
                        "schemaVersion", "1",
                        "payload", "{}"))
                .withStreamKey("stream:test")
                .withId(RecordId.of(recordId));
    }
}

package com.ulticode.modules.event.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.achievement.consumer.SubmissionJudgedAchievementConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import com.ulticode.modules.websocket.consumer.SubmissionJudgedWebSocketConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedInboxBridgeTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    @SuppressWarnings("rawtypes")
    private StreamOperations streamOperations;
    @Mock
    private ConsumerInboxMapper inboxMapper;
    @Mock
    private UuidGenerator uuidGenerator;
    @Mock
    private SubmissionJudgedNotificationConsumer notificationConsumer;
    @Mock
    private SubmissionJudgedAchievementConsumer achievementConsumer;
    @Mock
    private SubmissionJudgedWebSocketConsumer webSocketConsumer;
    @Test
    void stagesOneEventIntoBothOwnerInboxesBeforeAcknowledging() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn(
                "inbox-notification", "inbox-achievement", "inbox-websocket");
        when(inboxMapper.insertIfAbsent(anyString(), anyString(), eq("event-1"),
                eq("SubmissionJudged"), anyString())).thenReturn(1);

        MapRecord<String, String, String> record = record("event-1", "Accepted");
        doReturn(List.of(record), List.of(), List.of(record), List.of(), List.of(record), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));

        SubmissionJudgedInboxBridge bridge = bridge();

        int staged = bridge.consume();

        assertThat(staged).isEqualTo(3);
        verify(inboxMapper).insertIfAbsent(eq("inbox-notification"), eq("App-Notification"),
                eq("event-1"), eq("SubmissionJudged"), anyString());
        verify(inboxMapper).insertIfAbsent(eq("inbox-achievement"), eq("App-Achievement"),
                eq("event-1"), eq("SubmissionJudged"), anyString());
        verify(inboxMapper).insertIfAbsent(eq("inbox-websocket"), eq("App-WebSocket"),
                eq("event-1"), eq("SubmissionJudged"), anyString());
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-Notification"),
                (RecordId) eq(record.getId()));
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-Achievement"),
                (RecordId) eq(record.getId()));
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-WebSocket"),
                (RecordId) eq(record.getId()));
    }

    @Test
    void reclaimsIdlePendingStreamEntriesBeforeStaging() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn(
                "inbox-notification", "inbox-achievement", "inbox-websocket");
        when(inboxMapper.insertIfAbsent(anyString(), anyString(), eq("event-1"),
                eq("SubmissionJudged"), anyString())).thenReturn(1);

        MapRecord<String, String, String> record = record("event-1", "Accepted");
        PendingMessage pending = new PendingMessage(
                record.getId(), Consumer.from("old-group", "old-consumer"),
                Duration.ofSeconds(60), 2);
        when(streamOperations.pending(eq("stream:integration"), anyString(),
                any(Range.class), anyLong()))
                .thenReturn(new PendingMessages("group", List.of(pending)),
                        new PendingMessages("group", List.of(pending)),
                        new PendingMessages("group", List.of(pending)));
        when(streamOperations.claim(anyString(), anyString(), anyString(),
                any(Duration.class), any(RecordId[].class)))
                .thenReturn(List.of(record));

        int staged = bridge().consume();

        assertThat(staged).isEqualTo(3);
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-Notification"),
                (RecordId) eq(record.getId()));
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-Achievement"),
                (RecordId) eq(record.getId()));
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-WebSocket"),
                (RecordId) eq(record.getId()));
    }

    @Test
    void handlerFailureReturnsInboxRowToRetryableState() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(inboxMapper.claimLease(anyString(), anyString(), anyInt())).thenReturn(1, 0);

        ConsumerInboxRecord inboxRecord = new ConsumerInboxRecord();
        inboxRecord.setId("inbox-1");
        inboxRecord.setEventId("event-1");
        inboxRecord.setEventType("SubmissionJudged");
        inboxRecord.setPayload(Map.of("submissionId", "submission-1"));
        when(inboxMapper.selectLeased(anyString(), anyString())).thenReturn(List.of(inboxRecord));
        doThrow(new IllegalStateException("handler unavailable"))
                .when(notificationConsumer).consume(anyMap());

        bridge().consume();

        verify(inboxMapper).markFailed(
                eq("inbox-1"), eq("App-Notification"), anyString(), eq("handler unavailable"), eq(5));
        verify(inboxMapper, never()).markProcessed(eq("inbox-1"), anyString(), anyString());
    }

    @Test
    void malformedPayloadIsDurablyPoisonedForRetry() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-inbox");
        when(inboxMapper.insertIfAbsent(anyString(), eq("App-Notification"),
                eq("event-bad"), eq("IntegrationEventPoison"), anyString())).thenReturn(1);
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId", "event-bad",
                        "eventType", "SubmissionJudged",
                        "payload", "not-json"))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("2-0"));
        doReturn(List.of(record), List.of(), List.of(), List.of(), List.of(), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));

        int staged = bridge().consume();

        assertThat(staged).isEqualTo(1);
        verify(inboxMapper).insertIfAbsent(eq("poison-inbox"), eq("App-Notification"),
                eq("event-bad"), eq("IntegrationEventPoison"), anyString());
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-Notification"),
                (RecordId) eq(record.getId()));
    }

    @Test
    void nullPayloadIsDurablyPoisonedForRetry() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-inbox");
        when(inboxMapper.insertIfAbsent(anyString(), eq("App-Notification"),
                eq("event-null"), eq("IntegrationEventPoison"), anyString())).thenReturn(1);
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId", "event-null",
                        "eventType", "SubmissionJudged",
                        "payload", "null"))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("3-0"));
        doReturn(List.of(record), List.of(), List.of(), List.of(), List.of(), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));

        int staged = bridge().consume();

        assertThat(staged).isEqualTo(1);
        verify(inboxMapper).insertIfAbsent(eq("poison-inbox"), eq("App-Notification"),
                eq("event-null"), eq("IntegrationEventPoison"), anyString());
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-Notification"),
                (RecordId) eq(record.getId()));
    }

    @Test
    void oversizedEventIdIsDurablyPoisonedForRetry() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-inbox");
        when(inboxMapper.insertIfAbsent(anyString(), eq("App-Notification"),
                eq("poison:4-0"), eq("IntegrationEventPoison"), anyString())).thenReturn(1);
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId", "e".repeat(41),
                        "eventType", "SubmissionJudged",
                        "payload", "{\"submissionId\":\"submission-1\"}"))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("4-0"));
        doReturn(List.of(record), List.of(), List.of(), List.of(), List.of(), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));

        int staged = bridge().consume();

        assertThat(staged).isEqualTo(1);
        verify(inboxMapper).insertIfAbsent(eq("poison-inbox"), eq("App-Notification"),
                eq("poison:4-0"), eq("IntegrationEventPoison"), anyString());
        verify(streamOperations).acknowledge(eq("stream:integration"), eq("App-Notification"),
                (RecordId) eq(record.getId()));
    }

    @Test
    void stagingFailureLeavesStreamEntryPendingInsteadOfPoisoning() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(uuidGenerator.newId()).thenReturn("inbox-notification",
                "inbox-achievement", "inbox-websocket");
        doThrow(new IllegalStateException("database unavailable"))
                .when(inboxMapper)
                .insertIfAbsent(anyString(), eq("App-Notification"), eq("event-1"),
                        eq("SubmissionJudged"), anyString());

        MapRecord<String, String, String> record = record("event-1", "Accepted");
        doReturn(List.of(record), List.of(), List.of(), List.of(), List.of(), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));

        int staged = bridge().consume();

        assertThat(staged).isEqualTo(0);
        verify(inboxMapper).insertIfAbsent(anyString(), eq("App-Notification"),
                eq("event-1"), eq("SubmissionJudged"), anyString());
        verify(inboxMapper, never()).insertIfAbsent(anyString(), eq("App-Notification"),
                eq("event-1"), eq("IntegrationEventPoison"), anyString());
        verify(streamOperations, never()).acknowledge(
                eq("stream:integration"), eq("App-Notification"), (RecordId) eq(record.getId()));
    }

    @Test
    void acknowledgeFailureLeavesStagedEventPendingWithoutPoisoning() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("inbox-notification",
                "inbox-achievement", "inbox-websocket");
        when(inboxMapper.insertIfAbsent(anyString(), eq("App-Notification"), eq("event-1"),
                eq("SubmissionJudged"), anyString())).thenReturn(1);

        MapRecord<String, String, String> record = record("event-1", "Accepted");
        doThrow(new IllegalStateException("redis unavailable"))
                .when(streamOperations)
                .acknowledge(eq("stream:integration"), eq("App-Notification"),
                        (RecordId) eq(record.getId()));
        doReturn(List.of(record), List.of(), List.of(), List.of(), List.of(), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));

        int staged = bridge().consume();
        verify(inboxMapper).insertIfAbsent(anyString(), eq("App-Notification"),
                eq("event-1"), eq("SubmissionJudged"), anyString());

        assertThat(staged).isEqualTo(0);
        verify(inboxMapper, never()).insertIfAbsent(anyString(), eq("App-Notification"),
                eq("event-1"), eq("IntegrationEventPoison"), anyString());
        verify(streamOperations).acknowledge(
                eq("stream:integration"), eq("App-Notification"), (RecordId) eq(record.getId()));
    }

    private SubmissionJudgedInboxBridge bridge() {
        return new SubmissionJudgedInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                notificationConsumer,
                achievementConsumer,
                webSocketConsumer);
    }

    private static MapRecord<String, String, String> record(String eventId, String verdict) {
        return StreamRecords.mapBacked(Map.of(
                        "eventId", eventId,
                        "owner", "App",
                        "aggregateId", "submission-1",
                        "aggregateVersion", "7",
                        "eventType", "SubmissionJudged",
                        "schemaVersion", "1",
                        "payload", "{\"submissionId\":\"submission-1\","
                                + "\"userId\":\"user-1\",\"generation\":7,"
                                + "\"verdict\":\"" + verdict + "\"}"))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("1-0"));
    }
}

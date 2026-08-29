package com.ulticode.notification.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.redis.connection.stream.RecordId;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationIntegrationInboxBridgeTest {

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
    private SubmissionJudgedNotificationConsumer submissionConsumer;
    @Mock
    private NotificationIntentEventConsumer intentConsumer;

    @Test
    void rejectsNotificationIntentFromAnUnexpectedOwner() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-intent");
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId", "intent-foreign",
                        "owner", "Notification",
                        "eventType", "NotificationIntentCreated",
                        "aggregateId", "intent-1",
                        "aggregateVersion", "0",
                        "schemaVersion", "1",
                        "payload", "{}"))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("1-0"));
        doReturn(List.of(record), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(inboxMapper.insertIfAbsent(anyString(), eq("App-Notification"),
                eq("intent-foreign"), eq("IntegrationEventPoison"), anyString())).thenReturn(1);

        NotificationIntegrationInboxBridge bridge = new NotificationIntegrationInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                null,
                null,
                submissionConsumer,
                intentConsumer);

        int staged = bridge.consume();

        assertThat(staged).isEqualTo(1);
    }
}

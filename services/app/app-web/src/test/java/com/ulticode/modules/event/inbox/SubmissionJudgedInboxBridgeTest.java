package com.ulticode.modules.event.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.achievement.consumer.SubmissionJudgedAchievementConsumer;
import com.ulticode.modules.contest.consumer.SubmissionJudgedContestConsumer;
import com.ulticode.modules.websocket.consumer.SubmissionJudgedWebSocketConsumer;
import com.ulticode.modules.contest.consumer.SubmissionCreatedContestConsumer;
import com.ulticode.modules.moderation.consumer.UserBannedModerationConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/** Owner binding tests; generic Redis staging mechanics live in the shared bridge suite. */
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
    private SubmissionJudgedAchievementConsumer achievementConsumer;
    @Mock
    private SubmissionJudgedWebSocketConsumer webSocketConsumer;
    @Mock
    private SubmissionJudgedContestConsumer contestConsumer;
    @Mock
    private SubmissionCreatedContestConsumer createdContestConsumer;
    @Mock
    private UserBannedModerationConsumer bannedConsumer;

    @Test
    void rejectsSubmissionJudgedEventFromAnUnexpectedOwner() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-1", "poison-2", "poison-3");
        doReturn(List.of(record("event-foreign", "Accepted", "Notification")), List.of(),
                List.of(record("event-foreign", "Accepted", "Notification")), List.of(),
                List.of(record("event-foreign", "Accepted", "Notification")), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        int staged = bridge().consume();

        assertThat(staged).isZero();
        verify(inboxMapper, times(3)).insertIfAbsent(anyString(), anyString(),
                eq("event-foreign"), eq("IntegrationEventPoison"), anyString());
    }

    @Test
    void rejectsSubmissionCreatedEventFromAnUnexpectedOwner() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-created");
        MapRecord<String, String, String> record = eventRecord(
                "created-foreign", "App", "SubmissionCreated", "{}");
        doReturn(List.of(), List.of(), List.of(), List.of(), List.of(record), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(inboxMapper.insertIfAbsent(anyString(), eq("App-Contest"),
                eq("created-foreign"), eq("IntegrationEventPoison"), anyString())).thenReturn(1);

        int staged = bridge().consume();

        assertThat(staged).isEqualTo(1);
        verify(inboxMapper).insertIfAbsent(anyString(), eq("App-Contest"),
                eq("created-foreign"), eq("IntegrationEventPoison"), anyString());
    }

    @Test
    void rejectsUserBannedEventFromAnUnexpectedOwner() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-banned");
        MapRecord<String, String, String> record = eventRecord(
                "banned-foreign", "Auth", "UserBanned", "{}");
        doReturn(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(record), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(inboxMapper.insertIfAbsent(anyString(), eq("App-Moderation"),
                eq("banned-foreign"), eq("IntegrationEventPoison"), anyString())).thenReturn(1);

        int staged = bridgeWithModeration().consume();

        assertThat(staged).isEqualTo(1);
        verify(inboxMapper).insertIfAbsent(anyString(), eq("App-Moderation"),
                eq("banned-foreign"), eq("IntegrationEventPoison"), anyString());
    }

    private SubmissionJudgedInboxBridge bridge() {
        return new SubmissionJudgedInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                achievementConsumer,
                webSocketConsumer,
                contestConsumer,
                createdContestConsumer);
    }

    private SubmissionJudgedInboxBridge bridgeWithModeration() {
        org.springframework.beans.factory.ObjectProvider<UserBannedModerationConsumer> moderationProvider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        when(moderationProvider.getIfAvailable()).thenReturn(bannedConsumer);
        return new SubmissionJudgedInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                null,
                achievementConsumer,
                webSocketConsumer,
                contestConsumer,
                createdContestConsumer,
                moderationProvider);
    }

    private static MapRecord<String, String, String> record(
            String eventId, String verdict, String owner) {
        return eventRecord(eventId, owner, "SubmissionJudged",
                "{\"submissionId\":\"submission-1\","
                        + "\"userId\":\"user-1\",\"generation\":7,"
                        + "\"verdict\":\"" + verdict + "\"}");
    }

    private static MapRecord<String, String, String> eventRecord(
            String eventId, String owner, String eventType, String payload) {
        return StreamRecords.mapBacked(Map.of(
                        "eventId", eventId,
                        "owner", owner,
                        "aggregateId", "submission-1",
                        "aggregateVersion", "7",
                        "eventType", eventType,
                        "schemaVersion", "1",
                        "payload", payload))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("1-0"));
    }
}

package com.ulticode.modules.event.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.achievement.consumer.SubmissionJudgedAchievementConsumer;
import com.ulticode.modules.contest.consumer.SubmissionJudgedContestConsumer;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import com.ulticode.modules.websocket.consumer.SubmissionJudgedWebSocketConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NOTIFY-003: real-Redis evidence for the durable inbox staging seam.
 *
 * <p>The unit test {@link SubmissionJudgedInboxBridgeTest} proves the bridge
 * logic against a mocked Redis. This IT drives the same bridge against a real
 * {@code redis:7-alpine} Testcontainers instance to verify the transport
 * contract that mocks cannot: stream group creation, XREAD-into-MySQL staging,
 * group-scoped acknowledgement (no leftover pending entries), idempotent
 * acknowledgement of duplicate {@code eventId}s, and graceful degradation when
 * Redis is unreachable. The {@code ConsumerInboxMapper} and the four
 * consumers remain Mockito stubs because their seams are already covered by
 * {@link InboxConsumerIT} and the notification channel tests.
 */
@Testcontainers
@DisplayName("NOTIFY-003: SubmissionJudgedInboxBridge real-Redis IT")
class SubmissionJudgedInboxBridgeRedisIT {

    private static final String STREAM_KEY = "stream:integration";
    private static final String EVENT_TYPE = "SubmissionJudged";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static StringRedisTemplate realRedis;
    private static StringRedisTemplate unreachableRedis;
    private static ConsumerInboxMapper inboxMapper;
    private static SubmissionJudgedNotificationConsumer notificationConsumer;
    private static NotificationIntentEventConsumer notificationIntentConsumer;
    private static SubmissionJudgedAchievementConsumer achievementConsumer;
    private static SubmissionJudgedWebSocketConsumer webSocketConsumer;
    private static SubmissionJudgedContestConsumer contestConsumer;

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        realRedis = new StringRedisTemplate(factory);

        RedisStandaloneConfiguration deadConfig = new RedisStandaloneConfiguration("127.0.0.1", 1);
        LettuceConnectionFactory deadFactory = new LettuceConnectionFactory(deadConfig);
        deadFactory.afterPropertiesSet();
        unreachableRedis = new StringRedisTemplate(deadFactory);

        inboxMapper = mock(ConsumerInboxMapper.class);
        notificationConsumer = mock(SubmissionJudgedNotificationConsumer.class);
        notificationIntentConsumer = mock(NotificationIntentEventConsumer.class);
        achievementConsumer = mock(SubmissionJudgedAchievementConsumer.class);
        webSocketConsumer = mock(SubmissionJudgedWebSocketConsumer.class);
        contestConsumer = mock(SubmissionJudgedContestConsumer.class);
    }

    @AfterAll
    static void tearDown() {
        if (realRedis != null && realRedis.getConnectionFactory() instanceof LettuceConnectionFactory factory) {
            factory.destroy();
        }
        if (unreachableRedis != null && unreachableRedis.getConnectionFactory() instanceof LettuceConnectionFactory factory) {
            factory.destroy();
        }
    }

    @BeforeEach
    void resetStream() {
        realRedis.delete(STREAM_KEY);
    }

    private static SubmissionJudgedInboxBridge bridge(StringRedisTemplate redisTemplate) {
        return new SubmissionJudgedInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                UUID.randomUUID()::toString,
                notificationConsumer,
                notificationIntentConsumer,
                achievementConsumer,
                webSocketConsumer,
                contestConsumer);
    }

    private static RecordId xadd(String eventId, String verdict) {
        return realRedis.opsForStream().add(StreamRecords.mapBacked(Map.of(
                        "eventId", eventId,
                        "owner", "App",
                        "aggregateId", "submission-" + eventId,
                        "aggregateVersion", "1",
                        "eventType", EVENT_TYPE,
                        "schemaVersion", "1",
                        "payload", "{\"submissionId\":\"submission-" + eventId + "\","
                                + "\"userId\":\"user-1\",\"generation\":1,"
                                + "\"verdict\":\"" + verdict + "\"}"))
                .withStreamKey(STREAM_KEY));
    }

    @Test
    @DisplayName("real stream entry is staged into every owner inbox and acknowledged group-scoped")
    void stagesAndAcknowledgesRealStreamEntry() {
        RecordId id = xadd("real-1", "Accepted");
        when(inboxMapper.insertIfAbsent(anyString(), anyString(), eq("real-1"),
                eq(EVENT_TYPE), anyString())).thenReturn(1);
        when(inboxMapper.claimLease(anyString(), anyString(), eq(50))).thenReturn(0);

        SubmissionJudgedInboxBridge bridge = bridge(realRedis);
        int result = bridge.consume();

        assertThat(result).isGreaterThan(0);
        verify(inboxMapper, times(4)).insertIfAbsent(
                anyString(), anyString(), eq("real-1"), eq(EVENT_TYPE), anyString());
        for (String group : List.of("App-Notification", "App-Achievement",
                "App-WebSocket", "App-Contest")) {
            assertThat(realRedis.opsForStream().pending(
                    STREAM_KEY, group, Range.unbounded(), 100))
                    .as("group %s should have no pending entries after ack", group)
                    .isEmpty();
        }
        realRedis.opsForStream().delete(STREAM_KEY, id);
    }

    @Test
    @DisplayName("duplicate eventId is re-staged but the consumer inbox key absorbs the duplicate")
    void duplicateEventIsAckedAndConsumerKeyDedups() {
        RecordId first = xadd("dup-1", "Accepted");
        RecordId second = xadd("dup-1", "Accepted");
        assertThat(realRedis.opsForStream().size(STREAM_KEY))
                .as("both duplicate copies must be present in the stream")
                .isEqualTo(2);
        when(inboxMapper.insertIfAbsent(anyString(), anyString(), eq("dup-1"),
                eq(EVENT_TYPE), anyString())).thenReturn(1, 0);
        when(inboxMapper.claimLease(anyString(), anyString(), eq(50))).thenReturn(0);

        SubmissionJudgedInboxBridge bridge = bridge(realRedis);
        bridge.consume();

        // Both copies reach the idempotency seam; only the first returns 1,
        // mirroring the (consumer, event_id) unique constraint in MySQL.
        verify(inboxMapper, times(8)).insertIfAbsent(
                anyString(), anyString(), eq("dup-1"), eq(EVENT_TYPE), anyString());
        for (String group : List.of("App-Notification", "App-Achievement",
                "App-WebSocket", "App-Contest")) {
            assertThat(realRedis.opsForStream().pending(
                    STREAM_KEY, group, Range.unbounded(), 100))
                    .as("duplicate event should also be acked for group %s", group)
                    .isEmpty();
        }
        realRedis.opsForStream().delete(STREAM_KEY, first, second);
    }

    @Test
    @DisplayName("unreachable Redis degrades gracefully without poisoning the batch")
    void unreachableRedisIsTolerated() {
        SubmissionJudgedInboxBridge bridge = bridge(unreachableRedis);
        assertThatCode(bridge::consume)
                .as("Redis connection failure must be contained by the bridge")
                .doesNotThrowAnyException();
        assertThat(bridge.consume()).isZero();
    }
}

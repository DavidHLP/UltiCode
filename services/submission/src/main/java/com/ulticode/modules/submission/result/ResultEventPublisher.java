package com.ulticode.modules.submission.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes SubmissionJudged events to the shared {@code stream:integration}
 * Redis stream (SPLIT-003 slice-3, DEC-014).
 *
 * <p>backend-submission must not write the App-owned integration outbox table
 * (no cross-service SQL, DEC-011). The result outbox row itself is the durable
 * at-least-once channel; this publisher XADDs directly to the same stream the
 * App's {@code IntegrationOutboxDispatcher} writes, with an identical field
 * layout so existing consumers ({@code SubmissionJudgedInboxBridge} and
 * Notification) keep working after cutover.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultEventPublisher {

    private static final String STREAM_KEY = "stream:integration";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publish a SubmissionJudged event via Redis Streams XADD.
     *
     * @param eventId     outbox row id (stable idempotency key for consumers)
     * @param owner       publishing owner tag (kept "App" for consumer compatibility)
     * @param eventType   domain event type
     * @param aggregateId root aggregate identifier
     * @param generation  fence generation (aggregate version)
     * @param payload     event payload Map (serialized as JSON in the stream field)
     * @return the Redis-generated stream entry ID
     * @throws IllegalStateException when XADD fails
     */
    public String publish(String eventId, String owner, String eventType,
                          String aggregateId, long generation,
                          Map<String, Object> payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", eventId);
        fields.put("owner", owner);
        fields.put("aggregateId", aggregateId);
        fields.put("aggregateVersion", String.valueOf(generation));
        fields.put("eventType", eventType);
        fields.put("schemaVersion", "1");
        try {
            fields.put("payload", objectMapper.writeValueAsString(payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize result event payload", e);
        }

        MapRecord<String, String, String> streamRecord =
                StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY);

        RecordId recordId = redisTemplate.opsForStream().add(streamRecord);
        if (recordId == null) {
            throw new IllegalStateException("Redis XADD returned null for event " + eventId);
        }
        return recordId.getValue();
    }
}

package com.ulticode.modules.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * Dispatcher for the {@code integration_outbox} table (P6-OUTBOX-001).
 *
 * <p>Claims PENDING rows via CAS, publishes each to Redis Streams
 * ({@code stream:integration}), and marks DELIVERED only after XADD returns
 * a record ID. Failed publishes are retried with exponential backoff;
 * after {@code MAX_ATTEMPTS} the row goes to DEAD (DLQ).
 *
 * <p>Follows the same claim/dispatch/confirm pattern as {@code AuditOutboxDispatcher}
 * and {@code JudgeOutboxDispatcher}, but targets Redis Streams instead of an
 * in-JVM consumer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationOutboxDispatcher {

    private static final String STREAM_KEY = "stream:integration";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final String claimOwner = "integration-outbox-" + UUID.randomUUID();

    private final IntegrationOutboxMapper outboxMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * Scheduled dispatch loop. Runs every 2 seconds (configurable).
     *
     * @return number of events successfully published
     */
    @Scheduled(fixedDelayString = "${integration.outbox.dispatcher.interval-ms:2000}",
               initialDelayString = "5000")
    public int dispatch() {
        outboxMapper.reclaimStaleClaimed();
        int claimed = outboxMapper.claimPending(claimOwner, BATCH_SIZE);
        if (claimed == 0) {
            return 0;
        }

        List<IntegrationOutboxRecord> records = outboxMapper.selectClaimed(claimOwner);
        int published = 0;

        for (IntegrationOutboxRecord record : records) {
            try {
                String streamId = publishToStream(record);
                if (outboxMapper.markDelivered(record.getEventId(), claimOwner, streamId) > 0) {
                    published++;
                    log.debug("Published event {} to {} as {}",
                            record.getEventId(), STREAM_KEY, streamId);
                } else {
                    log.debug("Event {} was reclaimed before delivery confirmation",
                            record.getEventId());
                }
            } catch (Exception e) {
                log.error("Failed to publish event {}: {}", record.getEventId(), e.getMessage(), e);
                outboxMapper.markFailed(
                        record.getEventId(), claimOwner, truncate(e.getMessage(), 500), MAX_ATTEMPTS);
            }
        }

        if (published > 0) {
            log.debug("Dispatched {} integration outbox events", published);
        }
        return published;
    }

    /**
     * Publish a single event to Redis Streams via XADD.
     * Returns the Redis-generated stream entry ID.
     */
    private String publishToStream(IntegrationOutboxRecord record) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", record.getEventId());
        fields.put("owner", record.getOwner());
        fields.put("aggregateId", record.getAggregateId());
        fields.put("aggregateVersion", String.valueOf(record.getAggregateVersion()));
        fields.put("eventType", record.getEventType());
        fields.put("schemaVersion", String.valueOf(record.getSchemaVersion()));
        if (record.getCausationId() != null) {
            fields.put("causationId", record.getCausationId());
        }
        if (record.getTraceId() != null) {
            fields.put("traceId", record.getTraceId());
        }
        // Payload is serialized as JSON string to keep Redis field types simple
        fields.put("payload", new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(record.getPayload()));

        MapRecord<String, String, String> streamRecord =
                StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY);

        RecordId recordId = redisTemplate.opsForStream().add(streamRecord);

        if (recordId == null) {
            throw new IllegalStateException("Redis XADD returned null for event " + record.getEventId());
        }

        return recordId.getValue();
    }

    /**
     * Get the oldest undelivered event age in seconds for monitoring/metrics.
     */
    public Long getOldestOutboxAgeSeconds() {
        return outboxMapper.oldestOutboxAgeSeconds();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

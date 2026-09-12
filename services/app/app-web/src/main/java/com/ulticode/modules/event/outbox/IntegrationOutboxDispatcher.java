package com.ulticode.modules.event.outbox;

import com.ulticode.common.outbox.OutboxDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Dispatcher for the {@code integration_outbox} table (P6-OUTBOX-001).
 *
 * <p>Claims PENDING rows via CAS, publishes each to Redis Streams
 * ({@code stream:integration}), and marks DELIVERED only after XADD returns
 * a record ID. Failed publishes are retried with exponential backoff;
 * after {@code MAX_ATTEMPTS} the row goes to DEAD (DLQ).
 *
 * <p>Uses a bounded claim/dispatch/confirm cycle and publishes to Redis Streams
 * outside the database claim transaction.
 */
@Component
public class IntegrationOutboxDispatcher {

    private static final String STREAM_KEY = "stream:integration";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IntegrationOutboxMapper outboxMapper;
    private final StringRedisTemplate redisTemplate;
    private final OutboxDispatcher<IntegrationOutboxRecord> dispatcher;

    public IntegrationOutboxDispatcher(
            IntegrationOutboxMapper outboxMapper,
            StringRedisTemplate redisTemplate) {
        this.outboxMapper = outboxMapper;
        this.redisTemplate = redisTemplate;
        this.dispatcher = new OutboxDispatcher<>(
                "integration-outbox",
                new OutboxDispatcher.Adapter<>() {
                    @Override
                    public void reclaimStaleClaimed() {
                        IntegrationOutboxDispatcher.this.outboxMapper.reclaimStaleClaimed();
                    }

                    @Override
                    public int claimPending(String claimOwner, int limit) {
                        return IntegrationOutboxDispatcher.this.outboxMapper.claimPending(claimOwner, limit);
                    }

                    @Override
                    public List<IntegrationOutboxRecord> selectClaimed(String claimOwner) {
                        return IntegrationOutboxDispatcher.this.outboxMapper.selectClaimed(claimOwner);
                    }

                    @Override
                    public String publish(IntegrationOutboxRecord record) throws Exception {
                        return IntegrationOutboxDispatcher.this.publishToStream(record);
                    }

                    @Override
                    public int markDelivered(
                            IntegrationOutboxRecord record,
                            String claimOwner,
                            String publicationId) {
                        return IntegrationOutboxDispatcher.this.outboxMapper.markDelivered(
                                record.getEventId(), claimOwner, publicationId);
                    }

                    @Override
                    public int markFailed(
                            IntegrationOutboxRecord record,
                            String claimOwner,
                            String error,
                            int maxAttempts) {
                        return IntegrationOutboxDispatcher.this.outboxMapper.markFailed(
                                record.getEventId(), claimOwner, error, maxAttempts);
                    }

                    @Override
                    public String recordId(IntegrationOutboxRecord record) {
                        return record.getEventId();
                    }
                });
    }

    /**
     * Scheduled dispatch loop. Runs every 2 seconds (configurable).
     *
     * @return number of events successfully published
     */
    @Scheduled(fixedDelayString = "${integration.outbox.dispatcher.interval-ms:2000}",
               initialDelayString = "5000")
    public int dispatch() {
        return dispatcher.dispatch();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        dispatcher.beginDrain();
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
        fields.put("payload", OBJECT_MAPPER.writeValueAsString(record.getPayload()));

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
}

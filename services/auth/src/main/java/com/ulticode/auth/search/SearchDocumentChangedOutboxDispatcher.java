package com.ulticode.auth.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.outbox.OutboxDispatcher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dispatcher for {@code search_document_changed_outbox} (SEARCH-001 slice-b).
 *
 * <p>Claims PENDING rows (CAS), XADDs each event to {@code stream:integration}
 * using the same wire format as backend-submission's {@code ResultEventPublisher}
 * (DEC-014: direct XADD, no second outbox), and marks DELIVERED only after the
 * stream append succeeds. Stale CLAIMED rows are reclaimed after a lease and
 * retried up to {@code maxAttempts}; a terminal FAILED row is not re-enqueued.
 */
@Component
@ConditionalOnProperty(name = "auth.search.outbox.dispatcher.enabled", havingValue = "true")
public class SearchDocumentChangedOutboxDispatcher {
    private static final String STREAM_KEY = "stream:integration";
    private static final int LEASE_SECONDS = 120;
    private static final int RETRY_BACKOFF_SECONDS = 30;

    private final SearchDocumentChangedOutboxMapper outboxMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxDispatcher<SearchDocumentChangedOutboxRecord> dispatcher;

    public SearchDocumentChangedOutboxDispatcher(
            SearchDocumentChangedOutboxMapper outboxMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.dispatcher = new OutboxDispatcher<>(
                "auth-search-outbox",
                new OutboxDispatcher.Adapter<>() {
                    @Override
                    public void reclaimStaleClaimed() {
                        outboxMapper.reclaimStaleClaimed(LEASE_SECONDS);
                    }

                    @Override
                    public int claimPending(String claimOwner, int limit) {
                        return outboxMapper.claimPending(claimOwner, limit);
                    }

                    @Override
                    public List<SearchDocumentChangedOutboxRecord> selectClaimed(String claimOwner) {
                        return outboxMapper.selectClaimed(claimOwner);
                    }

                    @Override
                    public String publish(SearchDocumentChangedOutboxRecord record) throws Exception {
                        return SearchDocumentChangedOutboxDispatcher.this.publishToStream(record);
                    }

                    @Override
                    public int markDelivered(
                            SearchDocumentChangedOutboxRecord record,
                            String claimOwner,
                            String publicationId) {
                        return outboxMapper.markDelivered(record.getId(), claimOwner);
                    }

                    @Override
                    public int markFailed(
                            SearchDocumentChangedOutboxRecord record,
                            String claimOwner,
                            String error,
                            int maxAttempts) {
                        return outboxMapper.markRetry(
                                record.getId(), claimOwner,
                                error != null ? error : "XADD failed",
                                maxAttempts, RETRY_BACKOFF_SECONDS);
                    }

                    @Override
                    public String recordId(SearchDocumentChangedOutboxRecord record) {
                        return record.getId();
                    }
                });
    }

    @Scheduled(fixedDelayString = "${auth.search.outbox.dispatcher.interval-ms:2000}",
               initialDelayString = "5000")
    public int dispatch() {
        return dispatcher.dispatch();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        dispatcher.beginDrain();
    }

    private String publishToStream(SearchDocumentChangedOutboxRecord record) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", record.getId());
        fields.put("owner", record.getOwner());
        fields.put("aggregateId", record.getAggregateId());
        fields.put("aggregateVersion", String.valueOf(record.getAggregateVersion()));
        fields.put("eventType", record.getEventType());
        fields.put("schemaVersion", String.valueOf(record.getSchemaVersion()));
        fields.put("payload", objectMapper.writeValueAsString(record.getPayload()));

        MapRecord<String, String, String> streamRecord =
                StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY);
        RecordId recordId = redisTemplate.opsForStream().add(streamRecord);
        if (recordId == null) {
            throw new IllegalStateException("Redis XADD returned null for event " + record.getId());
        }
        return recordId.getValue();
    }
}

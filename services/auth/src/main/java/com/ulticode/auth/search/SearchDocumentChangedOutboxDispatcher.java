package com.ulticode.auth.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.search.outbox.dispatcher.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SearchDocumentChangedOutboxDispatcher {
    private static final String STREAM_KEY = "stream:integration";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    private static final int LEASE_SECONDS = 120;
    private static final int RETRY_BACKOFF_SECONDS = 30;

    private final SearchDocumentChangedOutboxMapper outboxMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final String claimOwner = "auth-search-" + UUID.randomUUID();

    @Scheduled(fixedDelayString = "${auth.search.outbox.dispatcher.interval-ms:2000}",
               initialDelayString = "5000")
    public int dispatch() {
        outboxMapper.reclaimStaleClaimed(LEASE_SECONDS);
        List<SearchDocumentChangedOutboxRecord> pending = outboxMapper.selectPending(BATCH_SIZE);
        if (pending.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (SearchDocumentChangedOutboxRecord record : pending) {
            if (outboxMapper.claim(record.getId(), claimOwner) == 0) {
                continue; // another replica claimed it
            }
            try {
                publishToStream(record);
                outboxMapper.markDelivered(record.getId(), claimOwner);
                delivered++;
            } catch (Exception e) {
                log.warn("Failed to dispatch search event {}: {}", record.getId(), e.getMessage());
                outboxMapper.markRetry(record.getId(), claimOwner,
                        e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : "XADD failed",
                        MAX_ATTEMPTS, RETRY_BACKOFF_SECONDS);
            }
        }
        return delivered;
    }

    private void publishToStream(SearchDocumentChangedOutboxRecord record) throws Exception {
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
    }
}

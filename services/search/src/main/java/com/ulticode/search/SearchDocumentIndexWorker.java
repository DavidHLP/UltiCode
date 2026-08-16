package com.ulticode.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.search.config.SearchWorkerProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SEARCH-002 indexing worker: consumes {@code SearchDocumentChanged} events
 * from {@code stream:integration} and is the sole MeiliSearch index writer.
 *
 * <p>At-least-once via the Redis Streams PEL: a record is XACKed only after
 * MeiliSearch accepted the write task; on failure it stays in the PEL and is
 * reclaimed on the next cycle. Every write is idempotent by document id
 * (upsert overwrites, delete is a no-op when absent), so replay is safe.
 * Entries that exhaust {@code maxAttempts} deliveries are atomically
 * dead-lettered to {@code dlqKey}. Non-{@code SearchDocumentChanged} events
 * on the shared stream are ACKed unprocessed (per-group cursor; other groups
 * keep their own delivery). Only allowlisted indexes are written.
 *
 * <p>Version ledger (DEC-016): each UPSERT carries the document version
 * (epoch millis, {@code aggregateVersion} envelope field). The worker keeps a
 * per-index Redis hash ({@code versionKeyPrefix}:{@code index}) of the last
 * written version per document. An incoming UPSERT whose version is strictly
 * older than the ledger entry is skipped (counted, still ACKed) so a backfill
 * snapshot can never overwrite a newer live write; equal versions rewrite
 * (idempotent, content-convergent). The version is also embedded in the
 * Meili document as {@code _aggregateVersion} for diff watermark and
 * observability. DELETEs clear the ledger entry.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "search.worker.enabled", havingValue = "true")
public class SearchDocumentIndexWorker {

    private static final String EVENT_TYPE = SearchDocumentChangedEventContract.EVENT_TYPE;
    private static final Duration CLAIM_MIN_IDLE = Duration.ofSeconds(30);
    private static final String DOCUMENT_VERSION_FIELD = "_aggregateVersion";

    private final StringRedisTemplate redisTemplate;
    private final Client meiliSearchClient;
    private final ObjectMapper objectMapper;
    private final SearchWorkerProperties props;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private final io.micrometer.core.instrument.Counter processedCounter;
    private final io.micrometer.core.instrument.Counter deadLetterCounter;
    private final io.micrometer.core.instrument.Counter staleCounter;

    public SearchDocumentIndexWorker(
            StringRedisTemplate redisTemplate,
            Client meiliSearchClient,
            ObjectMapper objectMapper,
            SearchWorkerProperties props,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meiliSearchClient = meiliSearchClient;
        this.objectMapper = objectMapper;
        this.props = props;
        this.meterRegistry = meterRegistry;
        this.processedCounter = meterRegistry.counter("search.worker.processed");
        this.deadLetterCounter = meterRegistry.counter("search.worker.deadlettered");
        this.staleCounter = meterRegistry.counter("search.worker.stale_skipped");
    }

    private final TypeReference<Map<String, Object>> payloadType = new TypeReference<>() {
    };

    @Scheduled(fixedDelayString = "${search.worker.interval-ms:2000}",
               initialDelayString = "5000")
    public int consume() {
        if (!ensureGroup()) {
            return 0;
        }
        int processed = 0;
        processed += drainPending();   // PEL: dead-letter + reclaim
        processed += drainNew();       // new entries
        return processed;
    }

    private boolean ensureGroup() {
        try {
            redisTemplate.opsForStream().createGroup(
                    props.getStreamKey(), ReadOffset.from("0-0"), props.getGroup());
            return true;
        } catch (RuntimeException e) {
            // Lettuce surfaces BUSYGROUP as a generic RedisSystemException;
            // decide by re-querying the group list instead (restart case).
            if (groupExists()) {
                return true;
            }
            log.warn("Stream group {} unavailable: {}", props.getGroup(), e.getMessage());
            return false;
        }
    }

    private boolean groupExists() {
        try {
            var groups = redisTemplate.opsForStream().groups(props.getStreamKey());
            if (groups == null) {
                return false;
            }
            for (var group : groups) {
                if (props.getGroup().equals(group.groupName())) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("Failed to list stream groups: {}", e.getMessage());
            return false;
        }
    }

    private int drainNew() {
        List<MapRecord<String, String, String>> records;
        try {
            records = readNew(redisTemplate.opsForStream());
        } catch (RuntimeException e) {
            log.debug("Stream read unavailable: {}", e.getMessage());
            return 0;
        }
        int processed = 0;
        for (MapRecord<String, String, String> record : records) {
            if (process(record)) {
                processed++;
            }
        }
        return processed;
    }

    private int drainPending() {
        List<MapRecord<String, String, String>> records;
        try {
            records = readPending(redisTemplate.opsForStream());
        } catch (RuntimeException e) {
            log.debug("Stream reclaim unavailable: {}", e.getMessage());
            return 0;
        }
        int processed = 0;
        for (MapRecord<String, String, String> record : records) {
            if (process(record)) {
                processed++;
            }
        }
        return processed;
    }

    private List<MapRecord<String, String, String>> readNew(StreamOperations<String, String, String> streams) {
        List<MapRecord<String, String, String>> records = streams.read(
                Consumer.from(props.getGroup(), props.getConsumerName()),
                StreamReadOptions.empty().count(props.getBatchSize()),
                StreamOffset.create(props.getStreamKey(), ReadOffset.lastConsumed()));
        return records == null ? List.of() : records;
    }

    /**
     * PEL pass: dead-letter entries that exhausted deliveries, then claim and
     * return the remaining pending records for reprocessing.
     */
    private List<MapRecord<String, String, String>> readPending(StreamOperations<String, String, String> streams) {
        PendingMessages pending = streams.pending(
                props.getStreamKey(), props.getGroup(), Range.unbounded(), props.getBatchSize());
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }

        List<RecordId> reclaimIds = new ArrayList<>(pending.size());
        for (PendingMessage message : pending) {
            if (message.getTotalDeliveryCount() > props.getMaxAttempts()) {
                deadLetter(streams, message);
            } else {
                reclaimIds.add(message.getId());
            }
        }
        if (reclaimIds.isEmpty()) {
            return List.of();
        }

        List<MapRecord<String, String, String>> reclaimed = streams.claim(
                props.getStreamKey(),
                props.getGroup(),
                props.getConsumerName(),
                CLAIM_MIN_IDLE,
                reclaimIds.toArray(RecordId[]::new));
        return reclaimed == null ? List.of() : reclaimed;
    }

    private void deadLetter(StreamOperations<String, String, String> streams, PendingMessage message) {
        try {
            List<MapRecord<String, String, String>> source = streams.range(
                    props.getStreamKey(),
                    Range.closed(message.getId().getValue(), message.getId().getValue()));
            MapRecord<String, String, String> record =
                    source == null || source.isEmpty() ? null : source.get(0);
            Map<String, String> fields = record == null ? Map.of() : record.getValue();
            streams.add(MapRecord.create(props.getDlqKey(), fields));
            streams.acknowledge(props.getStreamKey(), props.getGroup(), message.getId());
            deadLetterCounter.increment();
            log.error("Dead-lettered search event {} after {} deliveries (eventType={})",
                    message.getId().getValue(), message.getTotalDeliveryCount(),
                    fields.getOrDefault("eventType", "?"));
        } catch (RuntimeException e) {
            log.warn("Failed to dead-letter search event {}: {}", message.getId().getValue(), e.getMessage());
        }
    }

    private boolean process(MapRecord<String, String, String> record) {
        Map<String, String> fields = record.getValue();
        if (fields == null) {
            ack(record);
            return true;
        }
        if (!EVENT_TYPE.equals(fields.get("eventType"))) {
            // Not ours; ACK so this group's cursor advances without touching
            // other groups' delivery.
            ack(record);
            return true;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(fields.get("payload"), payloadType);
            String index = stringField(payload, SearchDocumentChangedEventContract.INDEX);
            String operation = stringField(payload, SearchDocumentChangedEventContract.OPERATION);
            if (!SearchDocumentChangedEventContract.SUPPORTED_INDEXES.contains(index)) {
                throw new IllegalArgumentException("unsupported search index: " + index);
            }
            String documentId = fields.get(SearchDocumentChangedEventContract.AGGREGATE_ID);
            if (documentId == null || documentId.isBlank()) {
                throw new IllegalArgumentException("missing aggregateId for search event");
            }
            long incomingVersion = parseVersion(fields.get(SearchDocumentChangedEventContract.AGGREGATE_VERSION));
            String versionKey = props.getVersionKeyPrefix() + ":" + index;

            if (SearchDocumentChangedEventContract.UPSERT.equals(operation)) {
                Object document = payload.get(SearchDocumentChangedEventContract.DOCUMENT);
                if (document == null) {
                    throw new IllegalArgumentException("UPSERT search event without document");
                }
                String existing = ledgerVersion(versionKey, documentId);
                if (existing != null && Long.parseLong(existing) > incomingVersion) {
                    // Stale snapshot (e.g. backfill racing a newer live write):
                    // skip the write but still ACK — the newer version is indexed.
                    staleCounter.increment();
                    ack(record);
                    return true;
                }
                Map<String, Object> doc = objectMapper.readValue(
                        objectMapper.writeValueAsString(document), payloadType);
                doc.put(DOCUMENT_VERSION_FIELD, incomingVersion);
                meiliSearchClient.index(index).addDocuments(objectMapper.writeValueAsString(doc));
                redisTemplate.opsForHash().put(versionKey, documentId, String.valueOf(incomingVersion));
            } else if (SearchDocumentChangedEventContract.DELETE.equals(operation)) {
                meiliSearchClient.index(index).deleteDocument(documentId);
                redisTemplate.opsForHash().delete(versionKey, documentId);
            } else {
                throw new IllegalArgumentException("unsupported operation: " + operation);
            }

            ack(record);
            processedCounter.increment();
            return true;
        } catch (Exception e) {
            // Leave in PEL: reclaimed on the next cycle, dead-lettered after
            // maxAttempts deliveries.
            log.warn("Search event {} processing failed (will retry): {}", record.getId().getValue(), e.getMessage());
            return false;
        }
    }

    /**
     * Last-written version for a document, or {@code null} when the ledger
     * has no entry (first write, or index rebuilt). An unparsable ledger
     * value is treated as absent so a corrupt entry cannot wedge the stream.
     */
    private String ledgerVersion(String versionKey, String documentId) {
        try {
            Object existing = redisTemplate.opsForHash().get(versionKey, documentId);
            if (existing == null) {
                return null;
            }
            Long.parseLong(String.valueOf(existing));
            return String.valueOf(existing);
        } catch (RuntimeException e) {
            log.warn("Ignoring unparsable ledger version for {} in {}", documentId, versionKey);
            return null;
        }
    }

    /**
     * Event version (epoch millis). Events published before the version
     * semantic (aggregateVersion=0) are treated as version 0: they can
     * never be stale-skipped and always write, which is safe for legacy
     * replay.
     */
    private long parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String stringField(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private void ack(MapRecord<String, String, String> record) {
        try {
            redisTemplate.opsForStream().acknowledge(props.getStreamKey(), props.getGroup(), record.getId());
        } catch (RuntimeException e) {
            log.warn("Failed to ACK search event {}: {}", record.getId().getValue(), e.getMessage());
        }
    }
}

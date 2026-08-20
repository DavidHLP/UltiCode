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
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.script.RedisScript;
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
 * (idempotent, content-convergent). DELETEs record a negative tombstone
 * version so an equal-or-older UPSERT cannot resurrect a deleted document.
 * The version is also embedded in the Meili document as
 * {@code _aggregateVersion} for diff watermark and observability.
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

    private static final RedisScript<Long> ATOMIC_DEAD_LETTER_SCRIPT = RedisScript.of("""
            local marked = redis.call('SET', KEYS[3], '1', 'NX', 'EX', ARGV[10])
            if marked then
                local added = redis.pcall('XADD', KEYS[2], '*',
                    'eventId', ARGV[1],
                    'owner', ARGV[2],
                    'eventType', ARGV[3],
                    'aggregateId', ARGV[4],
                    'aggregateVersion', ARGV[5],
                    'schemaVersion', ARGV[6],
                    'causationId', ARGV[7],
                    'traceId', ARGV[8],
                    'payload', ARGV[9])
                if type(added) == 'table' and added['err'] then
                    redis.call('DEL', KEYS[3])
                    return -1
                end
            end
            redis.call('XACK', KEYS[1], ARGV[11], ARGV[12])
            return 1
            """, Long.class);

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
        List<MapRecord<String, String, String>> source = streams.range(
                props.getStreamKey(),
                Range.closed(message.getId().getValue(), message.getId().getValue()));
        MapRecord<String, String, String> record =
                source == null || source.isEmpty() ? null : source.get(0);
        Map<String, String> fields = record == null ? Map.of() : record.getValue();
        String markerKey = props.getDlqKey() + ":seen:" + message.getId().getValue();
        Long result = redisTemplate.execute(
                ATOMIC_DEAD_LETTER_SCRIPT,
                List.of(props.getStreamKey(), props.getDlqKey(), markerKey),
                fields.getOrDefault("eventId", ""),
                fields.getOrDefault("owner", ""),
                fields.getOrDefault("eventType", ""),
                fields.getOrDefault("aggregateId", ""),
                fields.getOrDefault("aggregateVersion", ""),
                fields.getOrDefault("schemaVersion", ""),
                fields.getOrDefault("causationId", ""),
                fields.getOrDefault("traceId", ""),
                fields.getOrDefault("payload", ""),
                "86400",
                props.getGroup(),
                message.getId().getValue());
        if (result == null || result < 0L) {
            throw new IllegalStateException("Atomic Search DLQ transfer failed for " + message.getId());
        }
        deadLetterCounter.increment();
        log.error("Dead-lettered search event {} after {} deliveries (eventType={})",
                message.getId().getValue(), message.getTotalDeliveryCount(),
                fields.getOrDefault("eventType", "?"));
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
                if (isStale(existing, incomingVersion)) {
                    // Stale snapshot or a write older than a tombstone (e.g.
                    // backfill racing a newer live write or an unpublish):
                    // skip the write but still ACK — the newer state is indexed.
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
                // Tombstone the ledger with the delete's version (stored
                // negative): a later UPSERT that is not strictly newer than
                // the delete is skipped, so a stale backfill snapshot can
                // never resurrect a deleted document (DEC-016 revision).
                redisTemplate.opsForHash().put(
                        versionKey, documentId, "-" + incomingVersion);
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
     * Whether an incoming UPSERT must be skipped because the ledger already
     * holds a newer state for the document:
     * <ul>
     *   <li>positive ledger value = last written version: skip iff
     *       existing &gt; incoming (equal rewrites, idempotent);</li>
     *   <li>negative ledger value = tombstone version {@code -T} recorded by
     *       a DELETE: skip iff {@code T &gt;= incoming} (a delete never loses
     *       to an equal-or-older snapshot);</li>
     *   <li>absent/corrupt ledger: never skip (first write or self-heal).
     * </ul>
     */
    private boolean isStale(String existing, long incomingVersion) {
        if (existing == null) {
            return false;
        }
        try {
            long existingVersion = Long.parseLong(existing);
            if (existingVersion < 0) {
                return -existingVersion >= incomingVersion;
            }
            return existingVersion > incomingVersion;
        } catch (NumberFormatException e) {
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

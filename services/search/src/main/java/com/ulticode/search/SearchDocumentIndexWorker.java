package com.ulticode.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.search.adapter.SearchIndex;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.common.lifecycle.DrainGate;
import com.ulticode.common.metrics.WorkerSloMeters;
import com.ulticode.modules.event.inbox.RedisStreamQueueHealth;
import com.ulticode.modules.event.inbox.RedisStreamTransport;
import com.ulticode.search.config.SearchWorkerProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
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
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "search.worker.enabled", havingValue = "true")
public class SearchDocumentIndexWorker {

    private static final String EVENT_TYPE = SearchDocumentChangedEventContract.EVENT_TYPE;
    private static final Duration CLAIM_MIN_IDLE = Duration.ofSeconds(30);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SearchWorkerProperties props;

    private final io.micrometer.core.instrument.Counter processedCounter;
    private final io.micrometer.core.instrument.Counter deadLetterCounter;

    /** Queue/consumer SLO gauges (review 2026-08-25 P1), refreshed by {@link #consume()}. */
    private final WorkerSloMeters slo;

    /** Instance-unique consumer identity, resolved once at startup. */
    private final String consumerName;
    private final RedisStreamTransport streamTransport;
    private final RedisStreamQueueHealth queueHealth;
    private final DrainGate drainGate = new DrainGate();
    private final VersionedSearchDocumentWriter documentWriter;

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

    /**
     * How long a lock-busy event is deferred from reclaim/dead-lettering.
     * Must exceed {@link VersionedSearchDocumentWriter#DOCUMENT_LOCK_TTL} so the lease of a crashed
     * holder can expire before this event is eligible for reclaim again.
     */
    private static final long LOCK_WAIT_MAX_MS =
            VersionedSearchDocumentWriter.DOCUMENT_LOCK_TTL.toMillis() * 2;

    /**
     * Events deferred because another replica held the document lease
     * (record id value → deadline epoch millis). Membership suppresses
     * re-claiming (which would inflate the delivery count and eventually
     * dead-letter a perfectly valid event, including DELETEs).
     */
    private final java.util.Map<String, Long> lockWaitDeadline = new java.util.concurrent.ConcurrentHashMap<>();

    public SearchDocumentIndexWorker(
            StringRedisTemplate redisTemplate,
            SearchIndex searchIndex,
            ObjectMapper objectMapper,
            SearchWorkerProperties props,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
        this.processedCounter = meterRegistry.counter("search.worker.processed");
        this.deadLetterCounter = meterRegistry.counter("search.worker.deadlettered");
        this.documentWriter = new VersionedSearchDocumentWriter(redisTemplate, searchIndex, objectMapper, props,
                meterRegistry.counter("search.worker.stale_skipped"));
        this.slo = WorkerSloMeters.register(meterRegistry, "search.worker");
        this.consumerName = props.effectiveConsumerName();
        this.streamTransport = new RedisStreamTransport(
                redisTemplate, props.getStreamKey(), props.getGroup(), consumerName);
        this.queueHealth = new RedisStreamQueueHealth(redisTemplate);
        log.info("Search worker consumer identity: {} in group {}", this.consumerName, props.getGroup());
    }

    private final TypeReference<Map<String, Object>> payloadType = new TypeReference<>() {
    };

    @Scheduled(scheduler = "searchConsumeScheduler",
            fixedDelayString = "${search.worker.interval-ms:2000}",
               initialDelayString = "5000")
    public int consume() {
        if (!drainGate.tryEnter()) {
            return 0;
        }
        try {
            if (drainGate.isDraining()) {
                return 0;
            }
            if (!ensureGroup()) {
                slo.incrementFailures();
                return 0;
            }
            refreshSloGauges();
            int processed = 0;
            processed += drainPending();   // PEL: dead-letter + reclaim
            processed += drainNew();       // new entries
            if (processed > 0) {
                slo.markSuccess();
            }
            return processed;
        } finally {
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }

    /**
     * Review 2026-08-25 P1: queue/consumer SLO gauges, refreshed on every
     * consume cycle. Best-effort: any observation failure keeps the previous
     * gauge value (or UNKNOWN) without disturbing consumption.
     */
    private void refreshSloGauges() {
        RedisStreamQueueHealth.Snapshot snapshot = queueHealth.observe(
                props.getStreamKey(), props.getGroup(), props.getDlqKey());
        if (snapshot.queueLag() != WorkerSloMeters.UNKNOWN) {
            slo.setQueueLag(snapshot.queueLag());
        }
        if (snapshot.pelSize() != WorkerSloMeters.UNKNOWN) {
            slo.setPelSize(snapshot.pelSize());
        }
        if (snapshot.oldestPendingAgeSeconds() != WorkerSloMeters.UNKNOWN) {
            slo.setPelOldestAgeSeconds(snapshot.oldestPendingAgeSeconds());
        }
        if (snapshot.dlqSize() != WorkerSloMeters.UNKNOWN) {
            slo.setDlqSize(snapshot.dlqSize());
        }
    }

    private boolean ensureGroup() {
        if (drainGate.isDraining()) {
            return false;
        }
        boolean ready = streamTransport.ensureGroup();
        if (!ready) {
            log.warn("Stream group {} unavailable", props.getGroup());
        }
        return ready;
    }

    private int drainNew() {
        List<MapRecord<String, String, String>> records;
        try {
            records = readNew();
        } catch (RuntimeException e) {
            slo.incrementFailures();
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
            slo.incrementFailures();
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

    private List<MapRecord<String, String, String>> readNew() {
        if (drainGate.isDraining()) {
            return List.of();
        }
        return streamTransport.read(
                org.springframework.data.redis.connection.stream.ReadOffset.lastConsumed(),
                props.getBatchSize());
    }

    /**
     * PEL pass: dead-letter entries that exhausted deliveries, then claim and
     * return the remaining pending records for reprocessing. Events deferred
     * for a busy document lease are skipped entirely so a claim cannot
     * inflate their delivery count.
     */
    private List<MapRecord<String, String, String>> readPending(StreamOperations<String, String, String> streams) {
        if (drainGate.isDraining()) {
            return List.of();
        }
        PendingMessages pending = streamTransport.pending(props.getBatchSize());
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }

        pruneExpiredLockWaits();
        List<RecordId> reclaimIds = new ArrayList<>(pending.size());
        for (PendingMessage message : pending) {
            if (isWaitingForDocumentLock(message.getId())) {
                continue;
            }
            if (message.getTotalDeliveryCount() > props.getMaxAttempts()) {
                deadLetter(streams, message);
            } else {
                reclaimIds.add(message.getId());
            }
        }
        if (reclaimIds.isEmpty()) {
            return List.of();
        }
        return streamTransport.reclaim(reclaimIds, CLAIM_MIN_IDLE);
    }

    private boolean isWaitingForDocumentLock(RecordId recordId) {
        return lockWaitDeadline.containsKey(recordId.getValue());
    }

    private void pruneExpiredLockWaits() {
        long now = System.currentTimeMillis();
        lockWaitDeadline.values().removeIf(deadline -> deadline < now);
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
            return ack(record);
        }
        if (!EVENT_TYPE.equals(fields.get("eventType"))) {
            // Not ours; ACK so this group's cursor advances without touching
            // other groups.
            return ack(record);
        }

        try {
            IntegrationEventEnvelopeContract.requireCompatibleEnvelope(fields);
            String owner = fields.get(SearchDocumentChangedEventContract.OWNER_FIELD);
            if (owner == null || !SearchDocumentChangedEventContract.SUPPORTED_PUBLISHERS.contains(owner)) {
                throw new IllegalArgumentException("unsupported search event owner: " + owner);
            }

            Map<String, Object> payload = objectMapper.readValue(fields.get("payload"), payloadType);
            String index = stringField(payload, SearchDocumentChangedEventContract.INDEX);
            String operation = stringField(payload, SearchDocumentChangedEventContract.OPERATION);
            if (!SearchDocumentChangedEventContract.SUPPORTED_INDEXES.contains(index)) {
                throw new IllegalArgumentException("unsupported search index: " + index);
            }
            String expectedOwner = SearchDocumentChangedEventContract.USERS_INDEX.equals(index)
                    ? SearchDocumentChangedEventContract.AUTH_PUBLISHER
                    : SearchDocumentChangedEventContract.APP_PUBLISHER;
            if (!expectedOwner.equals(owner)) {
                throw new IllegalArgumentException(
                        "search event owner does not own index: " + owner + " -> " + index);
            }
            String documentId = fields.get(SearchDocumentChangedEventContract.AGGREGATE_ID);
            if (documentId == null || documentId.isBlank()) {
                throw new IllegalArgumentException("missing aggregateId for search event");
            }
            long incomingVersion = parseVersion(fields.get(SearchDocumentChangedEventContract.AGGREGATE_VERSION));

            String recordId = record.getId().getValue();
            VersionedSearchDocumentWriter.Result result = documentWriter.apply(
                    index, documentId, incomingVersion, operation,
                    payload.get(SearchDocumentChangedEventContract.DOCUMENT),
                    consumerName + ":" + recordId, () -> lockWaitDeadline.remove(recordId));
            if (result == VersionedSearchDocumentWriter.Result.LOCK_BUSY) {
                // Defer reclaim and dead-lettering while another replica holds the lease.
                lockWaitDeadline.put(recordId, System.currentTimeMillis() + LOCK_WAIT_MAX_MS);
                log.debug("Search event {} deferred: document lock busy", recordId);
                return false;
            }
            if (!ack(record)) {
                return false;
            }
            if (result == VersionedSearchDocumentWriter.Result.APPLIED) {
                processedCounter.increment();
            }
            return true;

        } catch (Exception e) {
            // Leave in PEL: reclaimed on the next cycle, dead-lettered after
            // maxAttempts deliveries.
            slo.incrementFailures();
            log.warn("Search event {} processing failed (will retry): {}", record.getId().getValue(), e.getMessage());
            return false;
        }
    }

    /**
     * Event version (epoch millis). Version zero remains valid for legacy events.
     */
    private long parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing aggregate version");
        }
        try {
            long version = Long.parseLong(raw);
            if (version < 0L) {
                throw new IllegalArgumentException("Negative aggregate version");
            }
            return version;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid aggregate version", e);
        }
    }

    private String stringField(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean ack(MapRecord<String, String, String> record) {
        try {
            Long acknowledged = streamTransport.acknowledge(record.getId());
            if (acknowledged == null || acknowledged < 1L) {
                slo.incrementFailures();
                log.warn("Search event {} was not acknowledged", record.getId().getValue());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            slo.incrementFailures();
            log.warn("Failed to ACK search event {}: {}", record.getId().getValue(), e.getMessage());
            return false;
        }
    }
}

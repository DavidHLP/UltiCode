package com.ulticode.notification.inbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.notification.api.event.NotificationIntentEventContract;
import com.ulticode.common.metrics.WorkerSloMeters;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.InboxConsumer;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Bridges the shared integration stream into owner-specific durable inboxes.
 *
 * <p>Redis is only the transport. Each consumer group first stages the event in
 * MySQL, then acknowledges the stream entry. The inbox workers therefore own
 * retry, lease reclaim, and handler failure semantics, while the
 * {@code (consumer, event_id)} key absorbs replay and duplicate XADD delivery.
 *
 * <p>Notification owns the stable {@code App-Notification} consumer group so
 * replay and rollback keep the existing durable inbox identity.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "ulticode.notification.worker.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationIntegrationInboxBridge {

    private static final String STREAM_KEY = "stream:integration";
    private static final String POISON_EVENT_TYPE = "IntegrationEventPoison";
    private static final String EVENT_TYPE = "SubmissionJudged";
    private static final String NOTIFICATION_EVENT_TYPE = NotificationIntentEventContract.EVENT_TYPE;
    private static final int MAX_EVENT_ID_LENGTH = 40;
    private static final int BATCH_SIZE = 50;
    private final StringRedisTemplate redisTemplate;
    private final ConsumerInboxMapper inboxMapper;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final TransactionTemplate transactionTemplate;
    private final List<Binding> bindings;
    /** Queue/consumer SLO gauges for the App-Notification staging bridge. */
    private final WorkerSloMeters slo;

    @Autowired
    public NotificationIntegrationInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            SubmissionJudgedNotificationConsumer notificationConsumer,
            NotificationIntentEventConsumer notificationIntentConsumer) {
        PlatformTransactionManager transactionManager = transactionManagerProvider == null
                ? null : transactionManagerProvider.getIfAvailable();
        TransactionTemplate transactionTemplate = transactionManager == null
                ? null : new TransactionTemplate(transactionManager);
        this.redisTemplate = redisTemplate;
        this.inboxMapper = inboxMapper;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.transactionTemplate = transactionTemplate;
        InboxConsumer notificationInbox = new InboxConsumer(
                inboxMapper, "App-Notification", transactionTemplate);
        notificationInbox.registerHandlerOutsideTransaction(
                EVENT_TYPE, (eventId, payload) -> notificationConsumer.consume(payload));
        notificationInbox.registerHandlerOutsideTransaction(
                NOTIFICATION_EVENT_TYPE,
                (eventId, payload) -> notificationIntentConsumer.consume(eventId, payload));
        notificationInbox.registerHandler(POISON_EVENT_TYPE,
                NotificationIntegrationInboxBridge::rejectPoison);
        this.bindings = List.of(new Binding("App-Notification", notificationInbox,
                Set.of(EVENT_TYPE, NOTIFICATION_EVENT_TYPE)));
        MeterRegistry meterRegistry = meterRegistryProvider == null
                ? null : meterRegistryProvider.getIfAvailable();
        this.slo = meterRegistry == null
                ? null : WorkerSloMeters.register(meterRegistry, "notification.inbox");
    }

    /**
     * Stage new result events and then run both durable inbox workers.
     *
     * @return number of newly staged plus successfully processed inbox rows
     */
    @Scheduled(fixedDelayString = "${integration.inbox.consumer.interval-ms:2000}",
               initialDelayString = "5000")
    public int consume() {
        try {
            int staged = 0;
            for (Binding binding : bindings) {
                staged += stage(binding);
            }

            int processed = 0;
            for (Binding binding : bindings) {
                processed += binding.inboxConsumer.consume();
            }
            if (slo != null) {
                refreshSloGauges();
                slo.markSuccess();
            }
            return staged + processed;
        } catch (RuntimeException e) {
            if (slo != null) {
                slo.incrementFailures();
            }
            throw e;
        }
    }

    /**
     * Review 2026-08-25 P1: queue/consumer SLO gauges for the shared
     * integration stream as seen by the App-Notification group. Best-effort:
     * observation failures never break staging/consumption.
     */
    private void refreshSloGauges() {
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            Long streamLength = streams.size(STREAM_KEY);
            long pelSize = 0;
            var groups = streams.groups(STREAM_KEY);
            if (groups != null) {
                for (var info : groups) {
                    if ("App-Notification".equals(info.groupName())) {
                        Long pending = info.pendingCount();
                        pelSize = pending == null ? 0 : pending;
                        break;
                    }
                }
            }
            slo.setPelSize(pelSize);
            slo.setQueueLag(streamLag(streamLength == null ? WorkerSloMeters.UNKNOWN : streamLength));
            long oldestAgeSeconds = oldestPendingAgeSeconds(streams);
            if (oldestAgeSeconds >= 0) {
                slo.setPelOldestAgeSeconds(oldestAgeSeconds);
            }
        } catch (RuntimeException e) {
            log.debug("SLO gauge refresh unavailable: {}", e.getMessage());
        }
    }

    /**
     * Lag from {@code XINFO GROUPS lag} (Redis >= 7; Spring Data does not map
     * the field). When the broker cannot answer, fall back to the raw stream
     * length as an upper-bound proxy, or UNKNOWN when even that is unknown.
     */
    private long streamLag(long fallback) {
        try {
            Object reply = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>)
                    conn -> conn.execute("XINFO", "GROUPS".getBytes(), STREAM_KEY.getBytes()));
            if (!(reply instanceof java.util.List<?> fields)) {
                return fallback;
            }
            for (int i = 0; i + 1 < fields.size(); i += 2) {
                Object rawField = fields.get(i);
                String field = rawField instanceof byte[] b
                        ? new String(b) : String.valueOf(rawField);
                if (!"lag".equalsIgnoreCase(field)) {
                    continue;
                }
                Object value = fields.get(i + 1);
                String lag = value instanceof Number n ? n.toString()
                        : value instanceof byte[] vb ? new String(vb) : String.valueOf(value);
                return Long.parseLong(lag.trim());
            }
            return fallback;
        } catch (RuntimeException e) {
            log.debug("XINFO GROUPS lag unavailable: {}", e.getMessage());
            return fallback;
        }
    }

    private long oldestPendingAgeSeconds(StreamOperations<String, String, String> streams) {
        try {
            PendingMessages pending = streams.pending(
                    STREAM_KEY, "App-Notification",
                    Range.unbounded(), 1);
            if (pending == null || pending.isEmpty()) {
                return 0;
            }
            PendingMessage oldest = pending.iterator().next();
            return Math.max(0L, oldest.getElapsedTimeSinceLastDelivery().getSeconds());
        } catch (RuntimeException e) {
            log.debug("PEL age unavailable: {}", e.getMessage());
            return -1;
        }
    }

    private int stage(Binding binding) {
        if (!ensureGroup(binding)) {
            return 0;
        }

        Set<String> seen = new HashSet<>();
        int staged = 0;
        staged += stageRecords(binding, reclaim(binding), seen);
        staged += stageRecords(binding, read(binding, ReadOffset.from("0-0")), seen);
        staged += stageRecords(binding, read(binding, ReadOffset.lastConsumed()), seen);
        return staged;
    }

    private int stageRecords(Binding binding, List<MapRecord<String, String, String>> records,
                             Set<String> seen) {
        int staged = 0;
        for (MapRecord<String, String, String> record : records) {
            if (seen.add(record.getId().getValue())) {
                staged += stageRecord(binding, record);
            }
        }
        return staged;
    }

    private List<MapRecord<String, String, String>> reclaim(Binding binding) {
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            PendingMessages pending = streams.pending(
                    STREAM_KEY, binding.group, Range.unbounded(), BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                return List.of();
            }

            List<RecordId> ids = new ArrayList<>();
            for (PendingMessage message : pending) {
                ids.add(message.getId());
            }
            List<MapRecord<String, String, String>> reclaimed = streams.claim(
                    STREAM_KEY,
                    binding.group,
                    binding.redisConsumerName(),
                    Duration.ofSeconds(30),
                    ids.toArray(RecordId[]::new));
            return reclaimed == null ? List.of() : reclaimed;
        } catch (RuntimeException e) {
            if (slo != null) {
                slo.incrementFailures();
            }
            log.debug("Integration stream reclaim unavailable for {}: {}",
                    binding.group, e.getMessage());
            return List.of();
        }
    }

    private List<MapRecord<String, String, String>> read(Binding binding, ReadOffset offset) {
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            List<MapRecord<String, String, String>> records = streams.read(
                    org.springframework.data.redis.connection.stream.Consumer.from(
                            binding.group,
                            binding.redisConsumerName()),
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(STREAM_KEY, offset));
            return records == null ? List.of() : records;
        } catch (RuntimeException e) {
            if (slo != null) {
                slo.incrementFailures();
            }
            log.debug("Integration stream read unavailable for {}: {}", binding.group, e.getMessage());
            return List.of();
        }
    }
    private int stageRecord(Binding binding, MapRecord<String, String, String> record) {
        Map<String, String> fields = record.getValue();
        String eventId = fields == null ? null : fields.get("eventId");
        String eventType;
        try {
            if (fields == null) {
                throw new IllegalArgumentException("Missing integration event fields");
            }
            eventId = required(fields, "eventId");
            if (eventId.length() > MAX_EVENT_ID_LENGTH) {
                throw new IllegalArgumentException("Integration event id exceeds 40 characters");
            }
            eventType = required(fields, "eventType");
        } catch (IllegalArgumentException e) {
            log.warn("Malformed integration event {} for {}: {}",
                    eventId, binding.group, e.getMessage());
            return stagePoison(binding, record, eventId, e);
        }

        if (!binding.accepts(eventType)) {
            try {
                // ACK is scoped to this dedicated group; other event-type
                // groups retain their own delivery independently.
                acknowledge(binding, record);
            } catch (RuntimeException e) {
                log.warn("Failed to acknowledge ignored integration event {} for {}: {}",
                        eventId, binding.group, e.getMessage());
            }
            return 0;
        }

        Map<String, Object> payload;
        try {
            String payloadJson = required(fields, "payload");
            payload = objectMapper.readValue(
                    payloadJson, new TypeReference<Map<String, Object>>() { });
            if (payload == null) {
                throw new IllegalArgumentException("Integration event payload must be a JSON object");
            }
        } catch (IllegalArgumentException | JsonProcessingException e) {
            log.warn("Malformed integration event {} for {}: {}",
                    eventId, binding.group, e.getMessage());
            return stagePoison(binding, record, eventId, e);
        }

        try {
            int inserted = inboxMapper.insertIfAbsent(
                    uuidGenerator.newId(),
                    binding.group,
                    eventId,
                    eventType,
                    objectMapper.writeValueAsString(payload));
            acknowledge(binding, record);
            return inserted;
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize integration event {} for {}: {}",
                    eventId, binding.group, e.getMessage());
            return 0;
        } catch (RuntimeException e) {
            if (slo != null) {
                slo.incrementFailures();
            }
            log.warn("Failed to stage or acknowledge integration event {} for {}: {}",
                    eventId, binding.group, e.getMessage());
            return 0;
        }
    }

    private int stagePoison(Binding binding, MapRecord<String, String, String> record,
                            String eventId, Exception failure) {
        String poisonEventId = eventId;
        if (poisonEventId == null || poisonEventId.isBlank() || poisonEventId.length() > 40) {
            poisonEventId = "poison:" + record.getId().getValue();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("streamId", record.getId().getValue());
            payload.put("fields", record.getValue());
            payload.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            int inserted = inboxMapper.insertIfAbsent(
                    uuidGenerator.newId(),
                    binding.group,
                    poisonEventId,
                    POISON_EVENT_TYPE,
                    objectMapper.writeValueAsString(payload));
            acknowledge(binding, record);
            return inserted;
        } catch (Exception poisonFailure) {
            log.warn("Failed to stage poison integration event {} for {}: {}",
                    poisonEventId, binding.group, poisonFailure.getMessage());
            return 0;
        }
    }

    private void acknowledge(Binding binding, MapRecord<String, String, String> record) {
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, binding.group, record.getId());
    }

    private boolean ensureGroup(Binding binding) {
        if (binding.groupReady) {
            return true;
        }
        try {
            redisTemplate.opsForStream().createGroup(
                    STREAM_KEY, ReadOffset.from("0-0"), binding.group);
            binding.groupReady = true;
            return true;
        } catch (RuntimeException e) {
            // Lettuce surfaces the BUSYGROUP response as RedisSystemException
            // ("Error in execution") whose message never contains "BUSYGROUP",
            // so message matching is unreliable. Decide by re-querying the
            // group list instead: an existing group is the common restart case.
            if (groupExists(binding.group)) {
                binding.groupReady = true;
                return true;
            }
            log.debug("Integration stream group {} unavailable: {}", binding.group, e.getMessage());
            return false;
        }
    }

    private boolean groupExists(String group) {
        try {
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(STREAM_KEY);
            if (groups == null) {
                return false;
            }
            return groups.stream()
                    .anyMatch(info -> group.equals(info.groupName()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void rejectPoison(Map<String, Object> payload) {
        throw new IllegalArgumentException("Poison integration event: " + payload.get("error"));
    }
    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing integration event field: " + key);
        }
        return value;
    }

    private static final class Binding {
        private final String group;
        private final InboxConsumer inboxConsumer;
        private final Set<String> eventTypes;
        private final String redisConsumerName;
        private boolean groupReady;
        private Binding(String group, InboxConsumer inboxConsumer, Set<String> eventTypes) {
            this.group = group;
            this.inboxConsumer = inboxConsumer;
            this.eventTypes = eventTypes;
            this.redisConsumerName = group + ":" + UUID.randomUUID();
        }

        private boolean accepts(String eventType) {
            return eventTypes.contains(eventType);
        }

        private String redisConsumerName() {
            return redisConsumerName;
        }
    }
}

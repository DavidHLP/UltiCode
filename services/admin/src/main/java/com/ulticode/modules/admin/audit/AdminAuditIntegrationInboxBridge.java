package com.ulticode.modules.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.lifecycle.DrainGate;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.InboxConsumer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stages owner audit events from Redis into Admin's durable inbox.
 *
 * <p>Redis acknowledgement happens only after the MySQL inbox insert. The
 * shared {@link InboxConsumer} then supplies idempotent processing, lease
 * reclaim, exponential retry and DEAD/DLQ handling.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "admin.audit.inbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AdminAuditIntegrationInboxBridge {

    private static final List<String> STREAM_KEYS = List.of(
            IntegrationEventEnvelopeContract.APP_AUDIT_STREAM_KEY,
            IntegrationEventEnvelopeContract.AUTH_AUDIT_STREAM_KEY);
    private static final String GROUP = "Admin-Audit";
    private static final String EVENT_TYPE = "AuditRecorded";
    private static final String POISON_EVENT_TYPE = "IntegrationEventPoison";
    private static final int MAX_EVENT_ID_LENGTH = 40;
    private static final int BATCH_SIZE = 50;

    private final StringRedisTemplate redisTemplate;
    private final ConsumerInboxMapper inboxMapper;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final InboxConsumer inboxConsumer;
    private final String redisConsumerName = GROUP + ":" + UUID.randomUUID();
    private final Set<String> readyStreams = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final DrainGate drainGate = new DrainGate();

    @Autowired
    public AdminAuditIntegrationInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            AdminAuditEventConsumer auditEventConsumer) {
        PlatformTransactionManager transactionManager = transactionManagerProvider == null
                ? null : transactionManagerProvider.getIfAvailable();
        TransactionTemplate transactionTemplate = transactionManager == null
                ? null : new TransactionTemplate(transactionManager);
        this.redisTemplate = redisTemplate;
        this.inboxMapper = inboxMapper;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.inboxConsumer = new InboxConsumer(inboxMapper, GROUP, transactionTemplate);
        this.inboxConsumer.registerHandlerWithEventId(EVENT_TYPE,
                (eventId, payload) -> auditEventConsumer.consume(eventId,
                        objectMapper.convertValue(payload, AdminAuditRecordedPayload.class)));
        this.inboxConsumer.registerHandler(POISON_EVENT_TYPE,
                AdminAuditIntegrationInboxBridge::rejectPoison);
    }

    @Scheduled(scheduler = "adminAuditScheduler",
            fixedDelayString = "${admin.audit.inbox.interval-ms:2000}",
            initialDelayString = "5000")
    public int consume() {
        if (!drainGate.tryEnter()) {
            return 0;
        }
        try {
            int staged = stage();
            return staged + inboxConsumer.consume();
        } finally {
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
        inboxConsumer.beginDrain();
    }

    private int stage() {
        if (drainGate.isDraining()) {
            return 0;
        }
        Set<String> seen = new HashSet<>();
        int staged = 0;
        for (String streamKey : STREAM_KEYS) {
            if (!ensureGroup(streamKey)) {
                continue;
            }
            staged += stageRecords(streamKey, reclaim(streamKey), seen);
            staged += stageRecords(streamKey, read(streamKey, ReadOffset.from("0-0")), seen);
            staged += stageRecords(streamKey, read(streamKey, ReadOffset.lastConsumed()), seen);
        }
        return staged;
    }

    private int stageRecords(String streamKey,
                             List<MapRecord<String, String, String>> records,
                             Set<String> seen) {
        int staged = 0;
        for (MapRecord<String, String, String> record : records) {
            if (seen.add(streamKey + ":" + record.getId().getValue())) {
                staged += stageRecord(record, streamKey);
            }
        }
        return staged;
    }

    private List<MapRecord<String, String, String>> reclaim(String streamKey) {
        if (drainGate.isDraining()) {
            return List.of();
        }
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            PendingMessages pending = streams.pending(
                    streamKey, GROUP, Range.unbounded(), BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                return List.of();
            }
            List<RecordId> ids = new ArrayList<>();
            for (PendingMessage message : pending) {
                ids.add(message.getId());
            }
            List<MapRecord<String, String, String>> reclaimed = streams.claim(
                    streamKey, GROUP, redisConsumerName, Duration.ofSeconds(30),
                    ids.toArray(RecordId[]::new));
            return reclaimed == null ? List.of() : reclaimed;
        } catch (RuntimeException e) {
            log.debug("Admin audit stream reclaim unavailable for {}: {}", streamKey, e.getMessage());
            return List.of();
        }
    }

    private List<MapRecord<String, String, String>> read(String streamKey, ReadOffset offset) {
        if (drainGate.isDraining()) {
            return List.of();
        }
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            List<MapRecord<String, String, String>> records = streams.read(
                    org.springframework.data.redis.connection.stream.Consumer.from(
                            GROUP, redisConsumerName),
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(streamKey, offset));
            return records == null ? List.of() : records;
        } catch (RuntimeException e) {
            log.debug("Admin audit stream read unavailable for {}: {}", streamKey, e.getMessage());
            return List.of();
        }
    }


    private int stageRecord(MapRecord<String, String, String> record, String streamKey) {
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
            return stagePoison(record, streamKey, eventId, e);
        }

        if (!EVENT_TYPE.equals(eventType)) {
            acknowledge(record, streamKey);
            return 0;
        }
        String expectedOwner = expectedOwner(streamKey);
        if (!expectedOwner.equals(fields.get("owner"))) {
            return stagePoison(record, streamKey, eventId,
                    new IllegalArgumentException("Unexpected audit stream owner"));
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(
                    required(fields, "payload"), new TypeReference<Map<String, Object>>() { });
            if (payload == null) {
                throw new IllegalArgumentException("Integration event payload must be a JSON object");
            }
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return stagePoison(record, streamKey, eventId, e);
        }

        try {
            int inserted = inboxMapper.insertIfAbsent(
                    uuidGenerator.newId(), GROUP, eventId, eventType,
                    objectMapper.writeValueAsString(payload));
            acknowledge(record, streamKey);
            return inserted;
        } catch (JsonProcessingException e) {
            return stagePoison(record, streamKey, eventId, e);
        } catch (RuntimeException e) {
            log.warn("Failed to stage or acknowledge Admin audit event {}: {}",
                    eventId, e.getMessage());
            return 0;
        }
    }

    private int stagePoison(MapRecord<String, String, String> record,
                            String streamKey, String eventId, Exception failure) {
        String poisonEventId = eventId;
        if (poisonEventId == null || poisonEventId.isBlank()
                || poisonEventId.length() > MAX_EVENT_ID_LENGTH) {
            poisonEventId = "poison:" + record.getId().getValue();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("streamId", record.getId().getValue());
            payload.put("fields", record.getValue());
            payload.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            int inserted = inboxMapper.insertIfAbsent(
                    uuidGenerator.newId(), GROUP, poisonEventId, POISON_EVENT_TYPE,
                    objectMapper.writeValueAsString(payload));
            acknowledge(record, streamKey);
            return inserted;
        } catch (Exception poisonFailure) {
            log.warn("Failed to stage Admin audit poison event {}: {}",
                    poisonEventId, poisonFailure.getMessage());
            return 0;
        }
    }

    private void acknowledge(MapRecord<String, String, String> record, String streamKey) {
        redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
    }

    private boolean ensureGroup(String streamKey) {
        if (readyStreams.contains(streamKey)) {
            return true;
        }
        try {
            redisTemplate.opsForStream().createGroup(
                    streamKey, ReadOffset.from("0-0"), GROUP);
            readyStreams.add(streamKey);
            return true;
        } catch (RuntimeException e) {
            if (groupExists(streamKey)) {
                readyStreams.add(streamKey);
                return true;
            }
            log.debug("Admin audit stream group unavailable for {}: {}", streamKey, e.getMessage());
            return false;
        }
    }
    private boolean groupExists(String streamKey) {
        try {
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
            return groups != null && groups.stream()
                    .anyMatch(info -> GROUP.equals(info.groupName()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void rejectPoison(Map<String, Object> payload) {
        throw new IllegalArgumentException("Poison integration event: " + payload.get("error"));
    }
    private static String expectedOwner(String streamKey) {
        if (IntegrationEventEnvelopeContract.APP_AUDIT_STREAM_KEY.equals(streamKey)) {
            return "App";
        }
        if (IntegrationEventEnvelopeContract.AUTH_AUDIT_STREAM_KEY.equals(streamKey)) {
            return "Auth";
        }
        throw new IllegalArgumentException("Unknown audit stream: " + streamKey);
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing integration event field: " + key);
        }
        return value;
    }
}

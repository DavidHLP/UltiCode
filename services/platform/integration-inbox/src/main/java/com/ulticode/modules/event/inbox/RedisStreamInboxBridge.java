package com.ulticode.modules.event.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import com.ulticode.common.uuid.UuidGenerator;
import java.time.Duration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Shared Redis Streams-to-durable-inbox staging boundary.
 *
 * <p>Bindings supply only the stream/group/event ownership and handlers. This
 * class owns group creation, PEL reclaim, bounded reads, validation, poison
 * staging, acknowledgement and the process-local drain gate.</p>
 */
public final class RedisStreamInboxBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStreamInboxBridge.class);
    private static final String POISON_EVENT_TYPE = "IntegrationEventPoison";
    private static final int MAX_EVENT_ID_LENGTH = 40;
    private static final int BATCH_SIZE = 50;

    private final ConsumerInboxMapper inboxMapper;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final List<Binding> bindings;
    private final Map<Binding, RedisStreamTransport> transports;
    private final Consumer<RuntimeException> stagingFailureObserver;
    private final com.ulticode.common.lifecycle.DrainGate drainGate =
            new com.ulticode.common.lifecycle.DrainGate();

    public RedisStreamInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            List<Binding> bindings) {
        this(redisTemplate, inboxMapper, objectMapper, uuidGenerator, bindings, ignored -> { });
    }

    public RedisStreamInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            List<Binding> bindings,
            Consumer<RuntimeException> stagingFailureObserver) {
        Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.inboxMapper = Objects.requireNonNull(inboxMapper, "inboxMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.uuidGenerator = Objects.requireNonNull(uuidGenerator, "uuidGenerator");
        this.bindings = List.copyOf(bindings);
        Map<Binding, RedisStreamTransport> transportMap = new IdentityHashMap<>();
        for (Binding binding : this.bindings) {
            transportMap.put(binding, new RedisStreamTransport(
                    redisTemplate,
                    binding.streamKey(),
                    binding.group(),
                    binding.redisConsumerName()));
        }
        this.transports = Map.copyOf(transportMap);
        this.stagingFailureObserver = Objects.requireNonNull(
                stagingFailureObserver, "stagingFailureObserver");
    }

    /** Stage transport records and process each distinct durable inbox once. */
    public int consume() {
        if (!drainGate.tryEnter()) {
            return 0;
        }
        try {
            int staged = 0;
            for (Binding binding : bindings) {
                staged += stage(binding);
            }

            int processed = 0;
            Set<InboxConsumer> consumers = new HashSet<>();
            for (Binding binding : bindings) {
                if (consumers.add(binding.inboxConsumer())) {
                    processed += binding.inboxConsumer().consume();
                }
            }
            return staged + processed;
        } finally {
            drainGate.leave();
        }
    }

    public void beginDrain() {
        drainGate.beginDrain();
        for (Binding binding : bindings) {
            binding.inboxConsumer().beginDrain();
        }
    }

    private int stage(Binding binding) {
        if (drainGate.isDraining() || !ensureGroup(binding)) {
            return 0;
        }
        Set<String> seen = new HashSet<>();
        int staged = 0;
        staged += stageRecords(binding, reclaim(binding), seen);
        staged += stageRecords(binding, read(binding, ReadOffset.from("0-0")), seen);
        staged += stageRecords(binding, read(binding, ReadOffset.lastConsumed()), seen);
        return staged;
    }

    private int stageRecords(
            Binding binding,
            List<MapRecord<String, String, String>> records,
            Set<String> seen) {
        int staged = 0;
        for (MapRecord<String, String, String> record : records) {
            if (record != null && record.getId() != null
                    && seen.add(record.getId().getValue())) {
                staged += stageRecord(binding, record);
            }
        }
        return staged;
    }

    private List<MapRecord<String, String, String>> reclaim(Binding binding) {
        if (drainGate.isDraining()) {
            return List.of();
        }
        try {
            RedisStreamTransport transport = transport(binding);
            return transport.reclaim(transport.pending(BATCH_SIZE), Duration.ofSeconds(30));
        } catch (RuntimeException exception) {
            observeFailure(exception);
            LOGGER.debug("Integration stream reclaim unavailable for {}: {}",
                    binding.group(), exception.getMessage());
            return List.of();
        }
    }

    private List<MapRecord<String, String, String>> read(Binding binding, ReadOffset offset) {
        if (drainGate.isDraining()) {
            return List.of();
        }
        try {
            return transport(binding).read(offset, BATCH_SIZE);
        } catch (RuntimeException exception) {
            observeFailure(exception);
            LOGGER.debug("Integration stream read unavailable for {}: {}",
                    binding.group(), exception.getMessage());
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
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Malformed integration event {} for {}: {}",
                    eventId, binding.group(), exception.getMessage());
            return stagePoison(binding, record, eventId, exception);
        }

        if (!binding.accepts(eventType)) {
            try {
                acknowledge(binding, record);
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to acknowledge ignored integration event {} for {}: {}",
                        eventId, binding.group(), exception.getMessage());
                observeFailure(exception);
            }
            return 0;
        }
        try {
            IntegrationEventEnvelopeContract.requireCompatibleEnvelope(fields);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Incompatible integration event {} for {}: {}",
                    eventId, binding.group(), exception.getMessage());
            return stagePoison(binding, record, eventId, exception);
        }
        if (!binding.ownerValidator().accepts(
                binding.streamKey(), eventType, fields.get("owner"))) {
            return stagePoison(binding, record, eventId,
                    new IllegalArgumentException("Unexpected integration event owner"));
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(
                    required(fields, "payload"), new TypeReference<Map<String, Object>>() { });
            if (payload == null) {
                throw new IllegalArgumentException("Integration event payload must be a JSON object");
            }
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            LOGGER.warn("Malformed integration event {} for {}: {}",
                    eventId, binding.group(), exception.getMessage());
            return stagePoison(binding, record, eventId, exception);
        }

        try {
            int inserted = inboxMapper.insertIfAbsent(
                    uuidGenerator.newId(),
                    binding.group(),
                    eventId,
                    eventType,
                    objectMapper.writeValueAsString(payload));
            acknowledge(binding, record);
            return inserted;
        } catch (JsonProcessingException exception) {
            observeFailure(new IllegalStateException(
                    "Failed to serialize integration event", exception));
            LOGGER.warn("Failed to serialize integration event {} for {}: {}",
                    eventId, binding.group(), exception.getMessage());
            return 0;
        } catch (RuntimeException exception) {
            observeFailure(exception);
            LOGGER.warn("Failed to stage or acknowledge integration event {} for {}: {}",
                    eventId, binding.group(), exception.getMessage());
            return 0;
        }
    }

    private int stagePoison(
            Binding binding,
            MapRecord<String, String, String> record,
            String eventId,
            Exception failure) {
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
                    uuidGenerator.newId(),
                    binding.group(),
                    poisonEventId,
                    POISON_EVENT_TYPE,
                    objectMapper.writeValueAsString(payload));
            acknowledge(binding, record);
            return inserted;
        } catch (Exception poisonFailure) {
            if (poisonFailure instanceof RuntimeException runtimeException) {
                observeFailure(runtimeException);
            }
            LOGGER.warn("Failed to stage poison integration event {} for {}: {}",
                    poisonEventId, binding.group(), poisonFailure.getMessage());
            return 0;
        }
    }

    private void acknowledge(Binding binding, MapRecord<String, String, String> record) {
        transport(binding).acknowledge(record.getId());
    }

    private boolean ensureGroup(Binding binding) {
        if (transport(binding).ensureGroup()) {
            return true;
        }
        RuntimeException exception = new IllegalStateException(
                "Integration stream group unavailable: " + binding.group());
        observeFailure(exception);
        LOGGER.debug("Integration stream group {} unavailable: {}",
                binding.group(), exception.getMessage());
        return false;
    }

    private RedisStreamTransport transport(Binding binding) {
        return transports.get(binding);
    }

    private void observeFailure(RuntimeException exception) {
        try {
            stagingFailureObserver.accept(exception);
        } catch (RuntimeException observerFailure) {
            LOGGER.debug("Integration staging failure observer unavailable", observerFailure);
        }
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing integration event field: " + key);
        }
        return value;
    }

    @FunctionalInterface
    public interface OwnerValidator {
        boolean accepts(String streamKey, String eventType, String owner);
    }

    /** Mutable binding state is intentionally local to one bridge instance. */
    public static final class Binding {
        private final String streamKey;
        private final String group;
        private final Set<String> eventTypes;
        private final InboxConsumer inboxConsumer;
        private final OwnerValidator ownerValidator;
        private final String redisConsumerName;

        public Binding(
                String streamKey,
                String group,
                Set<String> eventTypes,
                InboxConsumer inboxConsumer,
                OwnerValidator ownerValidator) {
            this.streamKey = Objects.requireNonNull(streamKey, "streamKey");
            this.group = Objects.requireNonNull(group, "group");
            this.eventTypes = Set.copyOf(eventTypes);
            this.inboxConsumer = Objects.requireNonNull(inboxConsumer, "inboxConsumer");
            this.ownerValidator = Objects.requireNonNull(ownerValidator, "ownerValidator");
            this.redisConsumerName = group + ":" + UUID.randomUUID();
        }

        public String streamKey() {
            return streamKey;
        }

        public String group() {
            return group;
        }

        public InboxConsumer inboxConsumer() {
            return inboxConsumer;
        }

        private boolean accepts(String eventType) {
            return eventTypes.contains(eventType);
        }

        private String redisConsumerName() {
            return redisConsumerName;
        }

        private OwnerValidator ownerValidator() {
            return ownerValidator;
        }

    }
}

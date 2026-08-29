package com.ulticode.modules.event.inbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.achievement.consumer.SubmissionJudgedAchievementConsumer;
import com.ulticode.modules.contest.consumer.SubmissionJudgedContestConsumer;
import com.ulticode.modules.contest.consumer.SubmissionCreatedContestConsumer;
import com.ulticode.modules.websocket.consumer.SubmissionJudgedWebSocketConsumer;
import com.ulticode.modules.moderation.consumer.UserBannedModerationConsumer;
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
 * <p>App keeps only the App-Achievement, App-WebSocket, and App-Contest
 * bindings. App-Contest handles both SubmissionCreated association events and
 * SubmissionJudged scoring events. Notification owns its own binding in
 * backend-notification.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "ulticode.app.inbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SubmissionJudgedInboxBridge {

    private static final String STREAM_KEY = "stream:integration";
    private static final String POISON_EVENT_TYPE = "IntegrationEventPoison";
    private static final String EVENT_TYPE = "SubmissionJudged";
    private static final int MAX_EVENT_ID_LENGTH = 40;
    private static final int BATCH_SIZE = 50;
    private final StringRedisTemplate redisTemplate;
    private final ConsumerInboxMapper inboxMapper;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final TransactionTemplate transactionTemplate;
    private final List<Binding> bindings;

    public SubmissionJudgedInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            SubmissionJudgedAchievementConsumer achievementConsumer,
            SubmissionJudgedWebSocketConsumer webSocketConsumer,
            SubmissionJudgedContestConsumer contestConsumer) {
        this(redisTemplate, inboxMapper, objectMapper, uuidGenerator, null,
                achievementConsumer, webSocketConsumer, contestConsumer, null, null);
    }

    public SubmissionJudgedInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            SubmissionJudgedAchievementConsumer achievementConsumer,
            SubmissionJudgedWebSocketConsumer webSocketConsumer,
            SubmissionJudgedContestConsumer contestConsumer,
            SubmissionCreatedContestConsumer createdContestConsumer) {
        this(redisTemplate, inboxMapper, objectMapper, uuidGenerator, null,
                achievementConsumer, webSocketConsumer, contestConsumer, createdContestConsumer, null);
    }

    @Autowired
    public SubmissionJudgedInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            SubmissionJudgedAchievementConsumer achievementConsumer,
            SubmissionJudgedWebSocketConsumer webSocketConsumer,
            SubmissionJudgedContestConsumer contestConsumer,
            SubmissionCreatedContestConsumer createdContestConsumer,
            ObjectProvider<UserBannedModerationConsumer> moderationConsumerProvider) {
        PlatformTransactionManager transactionManager = transactionManagerProvider == null
                ? null
                : transactionManagerProvider.getIfAvailable();
        TransactionTemplate transactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
        this.redisTemplate = redisTemplate;
        this.inboxMapper = inboxMapper;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.transactionTemplate = transactionTemplate;
        InboxConsumer achievementInbox = new InboxConsumer(
                inboxMapper, "App-Achievement", transactionTemplate);
        achievementInbox.registerHandler(EVENT_TYPE, achievementConsumer::consume);
        achievementInbox.registerHandler(POISON_EVENT_TYPE, SubmissionJudgedInboxBridge::rejectPoison);
        InboxConsumer webSocketInbox = new InboxConsumer(
                inboxMapper, "App-WebSocket", transactionTemplate);
        webSocketInbox.registerHandler(EVENT_TYPE, webSocketConsumer::consume);
        webSocketInbox.registerHandler(POISON_EVENT_TYPE, SubmissionJudgedInboxBridge::rejectPoison);
        InboxConsumer contestInbox = new InboxConsumer(
                inboxMapper, "App-Contest", transactionTemplate);
        contestInbox.registerHandler(EVENT_TYPE, contestConsumer::consume);
        if (createdContestConsumer != null) {
            contestInbox.registerHandler(
                    com.ulticode.submission.api.event.SubmissionLifecycleEventContract.CREATED_EVENT_TYPE,
                    createdContestConsumer::consume);
        }
        contestInbox.registerHandler(POISON_EVENT_TYPE, SubmissionJudgedInboxBridge::rejectPoison);
        Set<String> contestEventTypes = createdContestConsumer == null
                ? Set.of(EVENT_TYPE)
                : Set.of(EVENT_TYPE,
                        com.ulticode.submission.api.event.SubmissionLifecycleEventContract.CREATED_EVENT_TYPE);
        List<Binding> mutableBindings = new ArrayList<>(List.of(
                new Binding("App-Achievement", achievementInbox, Set.of(EVENT_TYPE)),
                new Binding("App-WebSocket", webSocketInbox, Set.of(EVENT_TYPE)),
                new Binding("App-Contest", contestInbox, contestEventTypes)));
        UserBannedModerationConsumer moderationConsumer = moderationConsumerProvider == null
                ? null
                : moderationConsumerProvider.getIfAvailable();
        if (moderationConsumer != null) {
            InboxConsumer moderationInbox = new InboxConsumer(
                    inboxMapper, "App-Moderation", transactionTemplate);
            moderationInbox.registerHandler(
                    UserBannedModerationConsumer.EVENT_TYPE, moderationConsumer::consume);
            moderationInbox.registerHandler(POISON_EVENT_TYPE, SubmissionJudgedInboxBridge::rejectPoison);
            mutableBindings.add(new Binding(
                    "App-Moderation", moderationInbox, Set.of(UserBannedModerationConsumer.EVENT_TYPE)));
        }
        this.bindings = List.copyOf(mutableBindings);
    }

    /**
     * Stage new result events and then run both durable inbox workers.
     *
     * @return number of newly staged plus successfully processed inbox rows
     */
    @Scheduled(fixedDelayString = "${integration.inbox.consumer.interval-ms:2000}",
               initialDelayString = "5000")
    public int consume() {
        int staged = 0;
        for (Binding binding : bindings) {
            staged += stage(binding);
        }

        int processed = 0;
        for (Binding binding : bindings) {
            processed += binding.inboxConsumer.consume();
        }
        return staged + processed;
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
        if (!expectedOwner(eventType, fields.get("owner"))) {
            return stagePoison(binding, record, eventId,
                    new IllegalArgumentException("Unexpected integration event owner"));
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
    private static boolean expectedOwner(String eventType, String owner) {
        return switch (eventType) {
            case EVENT_TYPE -> "App".equals(owner) || "Submission".equals(owner);
            case com.ulticode.submission.api.event.SubmissionLifecycleEventContract.CREATED_EVENT_TYPE ->
                    "Submission".equals(owner);
            case UserBannedModerationConsumer.EVENT_TYPE -> "moderation".equals(owner);
            default -> true;
        };
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

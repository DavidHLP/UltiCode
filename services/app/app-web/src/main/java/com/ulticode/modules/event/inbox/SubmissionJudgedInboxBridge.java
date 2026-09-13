package com.ulticode.modules.event.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.achievement.consumer.SubmissionJudgedAchievementConsumer;
import com.ulticode.modules.contest.consumer.SubmissionCreatedContestConsumer;
import com.ulticode.modules.contest.consumer.SubmissionJudgedContestConsumer;
import com.ulticode.modules.moderation.consumer.UserBannedModerationConsumer;
import com.ulticode.modules.websocket.consumer.SubmissionJudgedWebSocketConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registers App-owned bindings for the shared durable Redis Streams inbox.
 * Transport staging and group lifecycle are owned by
 * {@link RedisStreamInboxBridge}; App supplies only its handlers and owner
 * policy.
 */
@Component
@ConditionalOnProperty(
        name = "ulticode.app.inbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SubmissionJudgedInboxBridge {

    private static final String STREAM_KEY = "stream:integration";
    private static final String EVENT_TYPE = "SubmissionJudged";
    private static final String CREATED_EVENT_TYPE =
            com.ulticode.submission.api.event.SubmissionLifecycleEventContract.CREATED_EVENT_TYPE;
    private static final String POISON_EVENT_TYPE = "IntegrationEventPoison";

    private final RedisStreamInboxBridge bridge;

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

        InboxConsumer achievementInbox = new InboxConsumer(
                inboxMapper, "App-Achievement", transactionTemplate);
        achievementInbox.registerHandler(EVENT_TYPE, achievementConsumer::consume);
        achievementInbox.registerHandler(POISON_EVENT_TYPE,
                SubmissionJudgedInboxBridge::rejectPoison);

        InboxConsumer webSocketInbox = new InboxConsumer(
                inboxMapper, "App-WebSocket", transactionTemplate);
        webSocketInbox.registerHandler(EVENT_TYPE, webSocketConsumer::consume);
        webSocketInbox.registerHandler(POISON_EVENT_TYPE,
                SubmissionJudgedInboxBridge::rejectPoison);

        InboxConsumer contestInbox = new InboxConsumer(
                inboxMapper, "App-Contest", transactionTemplate);
        contestInbox.registerHandler(EVENT_TYPE, contestConsumer::consume);
        if (createdContestConsumer != null) {
            contestInbox.registerHandler(CREATED_EVENT_TYPE, createdContestConsumer::consume);
        }
        contestInbox.registerHandler(POISON_EVENT_TYPE,
                SubmissionJudgedInboxBridge::rejectPoison);
        Set<String> contestEventTypes = createdContestConsumer == null
                ? Set.of(EVENT_TYPE)
                : Set.of(EVENT_TYPE, CREATED_EVENT_TYPE);

        List<RedisStreamInboxBridge.Binding> bindings = new ArrayList<>(List.of(
                binding("App-Achievement", achievementInbox, Set.of(EVENT_TYPE)),
                binding("App-WebSocket", webSocketInbox, Set.of(EVENT_TYPE)),
                binding("App-Contest", contestInbox, contestEventTypes)));

        UserBannedModerationConsumer moderationConsumer = moderationConsumerProvider == null
                ? null
                : moderationConsumerProvider.getIfAvailable();
        if (moderationConsumer != null) {
            InboxConsumer moderationInbox = new InboxConsumer(
                    inboxMapper, "App-Moderation", transactionTemplate);
            moderationInbox.registerHandler(
                    UserBannedModerationConsumer.EVENT_TYPE, moderationConsumer::consume);
            moderationInbox.registerHandler(POISON_EVENT_TYPE,
                    SubmissionJudgedInboxBridge::rejectPoison);
            bindings.add(binding(
                    "App-Moderation",
                    moderationInbox,
                    Set.of(UserBannedModerationConsumer.EVENT_TYPE)));
        }

        this.bridge = new RedisStreamInboxBridge(
                redisTemplate, inboxMapper, objectMapper, uuidGenerator, bindings);
    }

    /** Stage shared events and process the App durable inbox bindings. */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${integration.inbox.consumer.interval-ms:2000}",
            initialDelayString = "5000")
    public int consume() {
        return bridge.consume();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        bridge.beginDrain();
    }

    private static RedisStreamInboxBridge.Binding binding(
            String group,
            InboxConsumer inboxConsumer,
            Set<String> eventTypes) {
        return new RedisStreamInboxBridge.Binding(
                STREAM_KEY,
                group,
                eventTypes,
                inboxConsumer,
                SubmissionJudgedInboxBridge::expectedOwner);
    }

    private static boolean expectedOwner(String streamKey, String eventType, String owner) {
        return switch (eventType) {
            case EVENT_TYPE -> "App".equals(owner) || "Submission".equals(owner);
            case CREATED_EVENT_TYPE -> "Submission".equals(owner);
            case UserBannedModerationConsumer.EVENT_TYPE -> "moderation".equals(owner);
            default -> true;
        };
    }

    private static void rejectPoison(java.util.Map<String, Object> payload) {
        throw new IllegalArgumentException("Poison integration event: " + payload.get("error"));
    }
}

package com.ulticode.notification.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.metrics.WorkerSloMeters;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.InboxConsumer;
import com.ulticode.modules.event.inbox.RedisStreamInboxBridge;
import com.ulticode.modules.event.inbox.RedisStreamQueueHealth;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import com.ulticode.notification.api.event.NotificationIntentEventContract;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Registers Notification's durable consumers for the shared integration stream. */
@Component
@ConditionalOnProperty(
        name = "ulticode.notification.worker.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationIntegrationInboxBridge {

    private static final String STREAM_KEY = "stream:integration";
    private static final String GROUP = "App-Notification";
    private static final String EVENT_TYPE = "SubmissionJudged";
    private static final String NOTIFICATION_EVENT_TYPE = NotificationIntentEventContract.EVENT_TYPE;
    private static final String POISON_EVENT_TYPE = "IntegrationEventPoison";

    private final RedisStreamInboxBridge bridge;
    private final RedisStreamQueueHealth queueHealth;
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
        this.queueHealth = new RedisStreamQueueHealth(redisTemplate);
        PlatformTransactionManager transactionManager = transactionManagerProvider == null
                ? null
                : transactionManagerProvider.getIfAvailable();
        TransactionTemplate transactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
        MeterRegistry meterRegistry = meterRegistryProvider == null
                ? null
                : meterRegistryProvider.getIfAvailable();
        this.slo = meterRegistry == null
                ? null
                : WorkerSloMeters.register(meterRegistry, "notification.inbox");

        InboxConsumer notificationInbox = new InboxConsumer(
                inboxMapper, GROUP, transactionTemplate);
        notificationInbox.registerHandlerOutsideTransaction(
                EVENT_TYPE, (eventId, payload) -> notificationConsumer.consume(payload));
        notificationInbox.registerHandlerOutsideTransaction(
                NOTIFICATION_EVENT_TYPE,
                (eventId, payload) -> notificationIntentConsumer.consume(eventId, payload));
        notificationInbox.registerHandler(POISON_EVENT_TYPE,
                NotificationIntegrationInboxBridge::rejectPoison);

        RedisStreamInboxBridge.Binding binding = new RedisStreamInboxBridge.Binding(
                STREAM_KEY,
                GROUP,
                Set.of(EVENT_TYPE, NOTIFICATION_EVENT_TYPE),
                notificationInbox,
                NotificationIntegrationInboxBridge::expectedOwner);
        this.bridge = new RedisStreamInboxBridge(
                redisTemplate,
                inboxMapper,
                objectMapper,
                uuidGenerator,
                List.of(binding),
                ignored -> {
                    if (this.slo != null) {
                        this.slo.incrementFailures();
                    }
                });
    }

    /** Stage shared events and process the Notification durable inbox. */
    @Scheduled(fixedDelayString = "${integration.inbox.consumer.interval-ms:2000}",
               initialDelayString = "5000")
    public int consume() {
        try {
            int processed = bridge.consume();
            if (slo != null) {
                refreshSloGauges();
                slo.markSuccess();
            }
            return processed;
        } catch (RuntimeException exception) {
            if (slo != null) {
                slo.incrementFailures();
            }
            throw exception;
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        bridge.beginDrain();
    }

    /** Best-effort queue and PEL gauges for the App-Notification group. */
    private void refreshSloGauges() {
        RedisStreamQueueHealth.Snapshot snapshot = queueHealth.observe(STREAM_KEY, GROUP, null);
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

    private static boolean expectedOwner(String streamKey, String eventType, String owner) {
        if (EVENT_TYPE.equals(eventType)) {
            return "App".equals(owner) || "Submission".equals(owner);
        }
        if (NOTIFICATION_EVENT_TYPE.equals(eventType)) {
            return "App".equals(owner);
        }
        return true;
    }

    private static void rejectPoison(java.util.Map<String, Object> payload) {
        throw new IllegalArgumentException("Poison integration event: " + payload.get("error"));
    }
}

package com.ulticode.notification.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.metrics.WorkerSloMeters;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.InboxConsumer;
import com.ulticode.modules.event.inbox.RedisStreamInboxBridge;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import com.ulticode.notification.api.event.NotificationIntentEventContract;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StreamOperations;
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

    private static final Logger LOGGER =
            LoggerFactory.getLogger(NotificationIntegrationInboxBridge.class);
    private static final String STREAM_KEY = "stream:integration";
    private static final String GROUP = "App-Notification";
    private static final String EVENT_TYPE = "SubmissionJudged";
    private static final String NOTIFICATION_EVENT_TYPE = NotificationIntentEventContract.EVENT_TYPE;
    private static final String POISON_EVENT_TYPE = "IntegrationEventPoison";

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamInboxBridge bridge;
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
        this.redisTemplate = redisTemplate;
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
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            Long streamLength = streams.size(STREAM_KEY);
            long pelSize = 0;
            var groups = streams.groups(STREAM_KEY);
            if (groups != null) {
                for (var info : groups) {
                    if (GROUP.equals(info.groupName())) {
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
        } catch (RuntimeException exception) {
            LOGGER.debug("SLO gauge refresh unavailable: {}", exception.getMessage());
        }
    }

    /** Read XINFO GROUPS lag, falling back to stream length when unavailable. */
    private long streamLag(long fallback) {
        try {
            Object reply = redisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                            connection.execute("XINFO", "GROUPS".getBytes(), STREAM_KEY.getBytes()));
            if (!(reply instanceof List<?> fields)) {
                return fallback;
            }
            for (int i = 0; i + 1 < fields.size(); i += 2) {
                Object rawField = fields.get(i);
                String field = rawField instanceof byte[] bytes
                        ? new String(bytes)
                        : String.valueOf(rawField);
                if (!"lag".equalsIgnoreCase(field)) {
                    continue;
                }
                Object value = fields.get(i + 1);
                String lag = value instanceof Number number
                        ? number.toString()
                        : value instanceof byte[] bytes
                                ? new String(bytes)
                                : String.valueOf(value);
                return Long.parseLong(lag.trim());
            }
            return fallback;
        } catch (RuntimeException exception) {
            LOGGER.debug("XINFO GROUPS lag unavailable: {}", exception.getMessage());
            return fallback;
        }
    }

    private long oldestPendingAgeSeconds(StreamOperations<String, String, String> streams) {
        try {
            PendingMessages pending = streams.pending(STREAM_KEY, GROUP, Range.unbounded(), 1);
            if (pending == null || pending.isEmpty()) {
                return 0;
            }
            PendingMessage oldest = pending.iterator().next();
            return Math.max(0L, oldest.getElapsedTimeSinceLastDelivery().getSeconds());
        } catch (RuntimeException exception) {
            LOGGER.debug("PEL age unavailable: {}", exception.getMessage());
            return -1;
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

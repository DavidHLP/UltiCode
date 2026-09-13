package com.ulticode.modules.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.InboxConsumer;
import com.ulticode.modules.event.inbox.RedisStreamInboxBridge;
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

/** Registers Admin's App/Auth audit bindings for the shared durable inbox. */
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

    private final RedisStreamInboxBridge bridge;

    @Autowired
    public AdminAuditIntegrationInboxBridge(
            StringRedisTemplate redisTemplate,
            ConsumerInboxMapper inboxMapper,
            ObjectMapper objectMapper,
            UuidGenerator uuidGenerator,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            AdminAuditEventConsumer auditEventConsumer) {
        PlatformTransactionManager transactionManager = transactionManagerProvider == null
                ? null
                : transactionManagerProvider.getIfAvailable();
        TransactionTemplate transactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);

        InboxConsumer inboxConsumer = new InboxConsumer(
                inboxMapper, GROUP, transactionTemplate);
        inboxConsumer.registerHandlerWithEventId(EVENT_TYPE,
                (eventId, payload) -> auditEventConsumer.consume(
                        eventId,
                        objectMapper.convertValue(payload, AdminAuditRecordedPayload.class)));
        inboxConsumer.registerHandler(POISON_EVENT_TYPE,
                AdminAuditIntegrationInboxBridge::rejectPoison);

        List<RedisStreamInboxBridge.Binding> bindings = STREAM_KEYS.stream()
                .map(streamKey -> new RedisStreamInboxBridge.Binding(
                        streamKey,
                        GROUP,
                        Set.of(EVENT_TYPE),
                        inboxConsumer,
                        AdminAuditIntegrationInboxBridge::expectedOwner))
                .toList();
        this.bridge = new RedisStreamInboxBridge(
                redisTemplate, inboxMapper, objectMapper, uuidGenerator, bindings);
    }

    @Scheduled(scheduler = "adminAuditScheduler",
            fixedDelayString = "${admin.audit.inbox.interval-ms:2000}",
            initialDelayString = "5000")
    public int consume() {
        return bridge.consume();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        bridge.beginDrain();
    }

    private static boolean expectedOwner(String streamKey, String eventType, String owner) {
        if (IntegrationEventEnvelopeContract.APP_AUDIT_STREAM_KEY.equals(streamKey)) {
            return "App".equals(owner);
        }
        if (IntegrationEventEnvelopeContract.AUTH_AUDIT_STREAM_KEY.equals(streamKey)) {
            return "Auth".equals(owner);
        }
        return false;
    }

    private static void rejectPoison(java.util.Map<String, Object> payload) {
        throw new IllegalArgumentException("Poison integration event: " + payload.get("error"));
    }
}

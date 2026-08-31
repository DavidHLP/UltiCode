package com.ulticode.auth.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.lifecycle.DrainGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Publishes committed Auth audit rows to the shared integration stream. */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.audit.outbox.dispatcher.enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AuthAuditOutboxDispatcher {

    private static final String STREAM_KEY = "stream:integration";
    private static final String EVENT_TYPE = "AuditRecorded";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RETRY_BACKOFF_SECONDS = 30;

    private final AuthAuditOutboxMapper outboxMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String claimOwner = "auth-audit-outbox-" + UUID.randomUUID();
    private final DrainGate drainGate = new DrainGate();

    @Scheduled(fixedDelayString = "${auth.audit.outbox.dispatcher.interval-ms:2000}",
            initialDelayString = "5000")
    public int dispatch() {
        if (!drainGate.tryEnter()) {
            return 0;
        }
        try {
            return dispatchClaimedBatch();
        } finally {
            drainGate.leave();
        }
    }

    private int dispatchClaimedBatch() {
        outboxMapper.reclaimStaleClaimed();
        List<AuthAuditOutboxRecord> pending = outboxMapper.selectPending(BATCH_SIZE);
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (AuthAuditOutboxRecord record : pending) {
            if (drainGate.isDraining()) {
                break;
            }
            if (outboxMapper.claim(record.getId(), claimOwner) == 0) {
                continue;
            }
            try {
                publishToStream(record);
                if (outboxMapper.markDelivered(record.getId(), claimOwner) > 0) {
                    delivered++;
                }
            } catch (Exception e) {
                outboxMapper.markRetry(record.getId(), claimOwner,
                        truncate(e.getMessage(), 500), MAX_ATTEMPTS,
                        RETRY_BACKOFF_SECONDS);
                log.warn("Failed to dispatch Auth audit event {}: {}",
                        record.getId(), e.getMessage());
            }
        }
        return delivered;
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }

    private void publishToStream(AuthAuditOutboxRecord record) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", record.getId());
        fields.put("owner", "Auth");
        fields.put("aggregateId", record.getId());
        fields.put("aggregateVersion", "0");
        fields.put("eventType", EVENT_TYPE);
        fields.put("schemaVersion", "1");
        fields.put("payload", objectMapper.writeValueAsString(payload(record)));
        MapRecord<String, String, String> streamRecord =
                StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY);
        RecordId recordId = redisTemplate.opsForStream().add(streamRecord);
        if (recordId == null) {
            throw new IllegalStateException("Redis XADD returned null for audit event " + record.getId());
        }
    }

    private static Map<String, Object> payload(AuthAuditOutboxRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auditId", record.getId());
        payload.put("performerId", record.getPerformerId());
        payload.put("userId", record.getUserId());
        payload.put("action", record.getAction());
        payload.put("entityType", record.getEntityType());
        payload.put("entityId", record.getEntityId());
        payload.put("oldValues", record.getOldValues());
        payload.put("newValues", record.getNewValues());
        payload.put("ipAddress", record.getIpAddress());
        payload.put("userAgent", record.getUserAgent());
        payload.put("createdAt", record.getCreatedAt() == null
                ? null : record.getCreatedAt().toString());
        return payload;
    }

    private static String truncate(String value, int maxLength) {
        return value == null ? "Audit dispatch failed" : value.substring(0, Math.min(maxLength, value.length()));
    }
}

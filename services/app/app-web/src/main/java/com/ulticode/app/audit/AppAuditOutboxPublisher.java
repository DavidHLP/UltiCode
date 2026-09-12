package com.ulticode.app.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import com.ulticode.common.outbox.AuditOutboxPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Publishes App audit rows to the owner-specific audit stream. */
@Component
@ConditionalOnProperty(name = "app.audit.outbox.dispatcher.enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AppAuditOutboxPublisher implements AuditOutboxPublisher<AppAuditOutboxRecord> {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String publish(AppAuditOutboxRecord record) throws Exception {
        var payload = IntegrationEventEnvelopeContract.auditPayload(
                record.getId(), record.getPerformerId(), record.getUserId(), record.getAction(),
                record.getEntityType(), record.getEntityId(), record.getOldValues(),
                record.getNewValues(), record.getIpAddress(), record.getUserAgent(),
                record.getCreatedAt() == null ? null : record.getCreatedAt().toString());
        Map<String, String> fields = IntegrationEventEnvelopeContract.auditEnvelope(
                record.getId(), IntegrationEventEnvelopeContract.APP_OWNER,
                objectMapper.writeValueAsString(payload));
        MapRecord<String, String, String> streamRecord = StreamRecords.mapBacked(fields)
                .withStreamKey(IntegrationEventEnvelopeContract.APP_AUDIT_STREAM_KEY);
        RecordId recordId = redisTemplate.opsForStream().add(streamRecord);
        if (recordId == null) {
            throw new IllegalStateException("Redis XADD returned null for audit event " + record.getId());
        }
        return recordId.getValue();
    }
}

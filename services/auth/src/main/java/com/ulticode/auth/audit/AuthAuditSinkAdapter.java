package com.ulticode.auth.audit;

import com.ulticode.common.audit.AuditSinkPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Auth-local production adapter for {@link AuditSinkPort}
 * (P7-AUDIT-SINK-OWNER-BINDING-001).
 *
 * <p>Writes audit records into Auth's local {@code audit_outbox} within the
 * caller's transaction. A local dispatcher publishes the committed row as an
 * event; Admin never reads Auth tables. Auth currently has no
 * {@code @Audited} sites; this adapter keeps the shared aspect wiring ready
 * for future owner-local audit calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthAuditSinkAdapter implements AuditSinkPort {

    private final AuthAuditOutboxMapper authAuditOutboxMapper;
    private final Clock clock;

    @Override
    public void log(String performerId,
                    String userId,
                    String action,
                    String entityType,
                    String entityId,
                    Map<String, Object> oldValues,
                    Map<String, Object> newValues,
                    String ipAddress,
                    String userAgent) {
        AuthAuditOutboxRecord record = new AuthAuditOutboxRecord();
        record.setPerformerId(performerId);
        record.setUserId(userId);
        record.setAction(action);
        record.setEntityType(entityType);
        record.setEntityId(entityId);
        record.setOldValues(oldValues);
        record.setNewValues(newValues);
        record.setIpAddress(ipAddress);
        record.setUserAgent(userAgent);
        record.setState("PENDING");
        LocalDateTime createdAt = LocalDateTime.now(clock);
        record.setCreatedAt(createdAt);
        record.setAttempts(0);
        record.setNextRetryAt(createdAt);

        authAuditOutboxMapper.insert(record);
        log.debug("Audit record written to Auth audit_outbox: {} by {}", action, performerId);
    }
}

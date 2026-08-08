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
 * <p>Writes audit records into {@code admin.audit_outbox} within the caller's
 * local transaction using the Auth datasource, per the standing audit rule:
 * audit writes never cross Dubbo, so a rollback of the business transaction
 * also rolls back its audit rows. Insert-only; the single outbox dispatcher
 * remains in backend-admin. Auth currently has no {@code @Audited} sites; this
 * adapter exists so the web-security {@code AuditAspect} wiring resolves and
 * future sites audit out of the box.
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
        record.setCreatedAt(LocalDateTime.now(clock));

        authAuditOutboxMapper.insert(record);
        log.debug("Audit record written to admin.audit_outbox: {} by {}", action, performerId);
    }
}

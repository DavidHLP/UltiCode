package com.ulticode.app.audit;

import com.ulticode.common.audit.AuditSinkPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * App-local production adapter for {@link AuditSinkPort}
 * (P7-AUDIT-SINK-OWNER-BINDING-001).
 *
 * <p>Writes audit records into {@code admin.audit_outbox} within the caller's
 * local transaction using the App datasource, per the standing audit rule:
 * audit writes never cross Dubbo, so a rollback of the business transaction
 * also rolls back its audit rows. Insert-only; the single outbox dispatcher
 * remains in backend-admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppAuditSinkAdapter implements AuditSinkPort {

    private final AppAuditOutboxMapper appAuditOutboxMapper;
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
        AppAuditOutboxRecord record = new AppAuditOutboxRecord();
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

        appAuditOutboxMapper.insert(record);
        log.debug("Audit record written to admin.audit_outbox: {} by {}", action, performerId);
    }
}

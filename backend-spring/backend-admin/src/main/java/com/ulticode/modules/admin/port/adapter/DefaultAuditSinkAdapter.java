package com.ulticode.modules.admin.port.adapter;

import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.modules.admin.outbox.mapper.AuditOutboxMapper;
import com.ulticode.modules.admin.outbox.AuditOutboxRecord;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link AuditSinkPort} (P3-AUDIT-001).
 *
 * <p>Writes audit records into the `audit_outbox` table within the active
 * transaction. During coexistence, the single dispatcher remains in
 * backend-legacy until the producer and dispatcher cutover gates pass.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAuditSinkAdapter implements AuditSinkPort {

    private final AuditOutboxMapper auditOutboxMapper;
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
        AuditOutboxRecord record = new AuditOutboxRecord();
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

        auditOutboxMapper.insert(record);
        log.debug("Audit record written to audit_outbox: {} by {}", action, performerId);
    }
}

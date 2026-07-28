package com.ulticode.modules.admin.port.adapter;

import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.modules.admin.outbox.AuditOutboxMapper;
import com.ulticode.modules.admin.outbox.AuditOutboxRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Production adapter for {@link AuditSinkPort} (P3-AUDIT-001).
 *
 * <p>Writes audit records into the `audit_outbox` table within the active
 * transaction. The records are subsequently consumed and fan-out persisted
 * to Admin's `audit_logs` table by {@link com.ulticode.modules.admin.outbox.AuditOutboxDispatcher}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAuditSinkAdapter implements AuditSinkPort {

    private final AuditOutboxMapper auditOutboxMapper;

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

        auditOutboxMapper.insert(record);
        log.debug("Audit record written to audit_outbox: {} by {}", action, performerId);
    }
}

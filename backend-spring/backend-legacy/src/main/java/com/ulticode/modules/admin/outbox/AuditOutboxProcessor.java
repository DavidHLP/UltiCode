package com.ulticode.modules.admin.outbox;

import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated Spring component for per-record outbox processing in individual
 * {@code REQUIRES_NEW} transactions (P3-AUDIT-001).
 *
 * <p>Extracted to avoid the Spring AOP self-invocation pitfall where internal method calls
 * bypass the transaction proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditOutboxProcessor {

    private final AuditOutboxMapper auditOutboxMapper;
    private final AuditLogMapper auditLogMapper;

    /**
     * Process a single audit outbox record in a new, isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRecordInNewTx(AuditOutboxRecord record) {
        AuditLog auditLog = new AuditLog();
        auditLog.setPerformerId(record.getPerformerId());
        auditLog.setUserId(record.getUserId());
        auditLog.setAction(record.getAction());
        auditLog.setEntityType(record.getEntityType());
        auditLog.setEntityId(record.getEntityId());
        auditLog.setOldValues(record.getOldValues());
        auditLog.setNewValues(record.getNewValues());
        auditLog.setIpAddress(record.getIpAddress());
        auditLog.setUserAgent(record.getUserAgent());

        auditLogMapper.insert(auditLog);
        auditOutboxMapper.markProcessed(record.getId());
    }

    /**
     * Mark an outbox record as failed in a new, isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedInNewTx(String recordId) {
        auditOutboxMapper.markFailed(recordId);
    }
}

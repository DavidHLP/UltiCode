package com.ulticode.modules.admin.outbox;
import com.ulticode.modules.admin.outbox.mapper.AuditOutboxMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In-JVM consumer that polls the `audit_outbox` table and fans out records
 * to Admin's `audit_logs` table (P3-AUDIT-001).
 *
 * <p>Delegates per-record persistence to {@link AuditOutboxProcessor} so each row is processed
 * inside a Spring AOP-proxied {@code REQUIRES_NEW} transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditOutboxDispatcher {

    private static final int BATCH_SIZE = 50;

    private final AuditOutboxMapper auditOutboxMapper;
    private final AuditOutboxProcessor auditOutboxProcessor;

    @Scheduled(fixedDelayString = "${audit.outbox.dispatcher.interval-ms:2000}", initialDelayString = "5000")
    public int dispatch() {
        List<AuditOutboxRecord> pendingRecords = auditOutboxMapper.claimPending(BATCH_SIZE);
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return 0;
        }

        int processedCount = 0;
        for (AuditOutboxRecord record : pendingRecords) {
            try {
                auditOutboxProcessor.processRecordInNewTx(record);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to dispatch audit outbox record {}: {}", record.getId(), e.getMessage(), e);
                try {
                    auditOutboxProcessor.markFailedInNewTx(record.getId());
                } catch (Exception ex) {
                    log.error("Failed to mark audit outbox record {} as failed: {}", record.getId(), ex.getMessage(), ex);
                }
            }
        }

        if (processedCount > 0) {
            log.debug("Dispatched {} audit outbox records to audit_logs", processedCount);
        }
        return processedCount;
    }
}

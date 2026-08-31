package com.ulticode.modules.admin.outbox;
import com.ulticode.modules.admin.outbox.mapper.AuditOutboxMapper;
import com.ulticode.common.lifecycle.DrainGate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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
    private final DrainGate drainGate = new DrainGate();

    @Scheduled(scheduler = "adminAuditScheduler",
            fixedDelayString = "${audit.outbox.dispatcher.interval-ms:2000}", initialDelayString = "5000")
    public int dispatch() {
        if (!drainGate.tryEnter()) {
            return 0;
        }
        try {
        // Recover rows where JVM/DB died after claim() but before processor completed.
        auditOutboxMapper.reclaimStaleClaimed();
        List<AuditOutboxRecord> pendingRecords = auditOutboxMapper.claimPending(BATCH_SIZE);
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return 0;
        }

        int processedCount = 0;
        for (AuditOutboxRecord record : pendingRecords) {
            if (drainGate.isDraining()) {
                break;
            }
            String rowOwner = "audit-outbox-" + UUID.randomUUID();
            if (auditOutboxMapper.claim(record.getId(), rowOwner) == 0) {
                continue;
            }
            // Carry owner through record so terminal updates can fence late workers.
            record.setClaimOwner(rowOwner);
            try {
                auditOutboxProcessor.processRecordInNewTx(record);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to dispatch audit outbox record {}: {}", record.getId(), e.getMessage(), e);
                try {
                    auditOutboxProcessor.markFailedInNewTx(record.getId(), rowOwner);
                } catch (Exception ex) {
                    log.error("Failed to mark audit outbox record {} as failed: {}", record.getId(), ex.getMessage(), ex);
                }
            }
        }

        if (processedCount > 0) {
            log.debug("Dispatched {} audit outbox records to audit_logs", processedCount);
        }
        return processedCount;
        } finally {
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }
}

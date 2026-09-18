package com.ulticode.modules.admin.outbox;

import com.ulticode.common.outbox.AuditOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Sinks Admin audit rows locally; no Redis publication is required. */
@Component
@RequiredArgsConstructor
public class AdminAuditOutboxPublisher implements AuditOutboxPublisher<AuditOutboxRecord> {

    private final AuditOutboxProcessor auditOutboxProcessor;

    @Override
    public String publish(AuditOutboxRecord record) {
        AuditOutboxOutcome outcome = auditOutboxProcessor.processRecordInNewTx(record);
        if (outcome == AuditOutboxOutcome.LOST_CLAIM) {
            // The shared dispatcher records failures raised from publish(); a
            // lost claim must take that path so it is not counted as delivered.
            throw new IllegalStateException("Audit outbox record is no longer PROCESSING for owner "
                    + record.getClaimOwner() + ": " + record.getId());
        }
        return null;
    }
}

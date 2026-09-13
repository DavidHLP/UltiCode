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
        auditOutboxProcessor.processRecordInNewTx(record);
        return null;
    }
}

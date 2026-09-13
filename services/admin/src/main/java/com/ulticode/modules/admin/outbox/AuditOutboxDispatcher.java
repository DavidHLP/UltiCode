package com.ulticode.modules.admin.outbox;

import com.ulticode.common.outbox.AuditOutboxPublisher;
import com.ulticode.common.outbox.OutboxDispatcher;
import com.ulticode.modules.admin.outbox.mapper.AuditOutboxMapper;
import java.util.List;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispatches Admin audit rows through the shared lifecycle into the local sink. */
@Component
public class AuditOutboxDispatcher {

    private final OutboxDispatcher<AuditOutboxRecord> dispatcher;

    public AuditOutboxDispatcher(
            AuditOutboxMapper auditOutboxMapper,
            AuditOutboxProcessor auditOutboxProcessor,
            AuditOutboxPublisher<AuditOutboxRecord> publisher) {
        this.dispatcher = new OutboxDispatcher<>(
                "admin-audit-outbox",
                new OutboxDispatcher.Adapter<>() {
                    @Override
                    public void reclaimStaleClaimed() {
                        auditOutboxMapper.reclaimStaleClaimed();
                    }

                    @Override
                    public int claimPending(String claimOwner, int limit) {
                        return auditOutboxMapper.claimPending(claimOwner, limit);
                    }

                    @Override
                    public List<AuditOutboxRecord> selectClaimed(String claimOwner) {
                        return auditOutboxMapper.selectClaimed(claimOwner);
                    }

                    @Override
                    public String publish(AuditOutboxRecord record) throws Exception {
                        return publisher.publish(record);
                    }

                    @Override
                    public int markDelivered(
                            AuditOutboxRecord record,
                            String claimOwner,
                            String publicationId) {
                        // AdminAuditOutboxPublisher confirms the fenced local sink
                        // inside its REQUIRES_NEW transaction before returning.
                        return 1;
                    }

                    @Override
                    public int markFailed(
                            AuditOutboxRecord record,
                            String claimOwner,
                            String error,
                            int maxAttempts) {
                        auditOutboxProcessor.markFailedInNewTx(record.getId(), claimOwner);
                        return 1;
                    }

                    @Override
                    public String recordId(AuditOutboxRecord record) {
                        return record.getId();
                    }
                });
    }

    @Scheduled(scheduler = "adminAuditScheduler",
            fixedDelayString = "${audit.outbox.dispatcher.interval-ms:2000}", initialDelayString = "5000")
    public int dispatch() {
        return dispatcher.dispatch();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        dispatcher.beginDrain();
    }
}

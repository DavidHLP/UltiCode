package com.ulticode.app.audit;

import com.ulticode.common.outbox.AuditOutboxPublisher;
import com.ulticode.common.outbox.OutboxDispatcher;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispatches App audit rows through the shared outbox lifecycle. */
@Component
@ConditionalOnProperty(name = "app.audit.outbox.dispatcher.enabled",
        havingValue = "true", matchIfMissing = true)
public class AppAuditOutboxDispatcher {

    private static final int RETRY_BACKOFF_SECONDS = 30;

    private final OutboxDispatcher<AppAuditOutboxRecord> dispatcher;

    public AppAuditOutboxDispatcher(
            AppAuditOutboxMapper outboxMapper,
            AuditOutboxPublisher<AppAuditOutboxRecord> publisher) {
        this.dispatcher = new OutboxDispatcher<>(
                "app-audit-outbox",
                new OutboxDispatcher.Adapter<>() {
                    @Override
                    public void reclaimStaleClaimed() {
                        outboxMapper.reclaimStaleClaimed();
                    }

                    @Override
                    public int claimPending(String claimOwner, int limit) {
                        return outboxMapper.claimPending(claimOwner, limit);
                    }

                    @Override
                    public List<AppAuditOutboxRecord> selectClaimed(String claimOwner) {
                        return outboxMapper.selectClaimed(claimOwner);
                    }

                    @Override
                    public String publish(AppAuditOutboxRecord record) throws Exception {
                        return publisher.publish(record);
                    }

                    @Override
                    public int markDelivered(
                            AppAuditOutboxRecord record,
                            String claimOwner,
                            String publicationId) {
                        return outboxMapper.markDelivered(record.getId(), claimOwner);
                    }

                    @Override
                    public int markFailed(
                            AppAuditOutboxRecord record,
                            String claimOwner,
                            String error,
                            int maxAttempts) {
                        return outboxMapper.markRetry(
                                record.getId(), claimOwner, error, maxAttempts,
                                RETRY_BACKOFF_SECONDS);
                    }

                    @Override
                    public String recordId(AppAuditOutboxRecord record) {
                        return record.getId();
                    }
                });
    }

    @Scheduled(fixedDelayString = "${app.audit.outbox.dispatcher.interval-ms:2000}",
            initialDelayString = "5000")
    public int dispatch() {
        return dispatcher.dispatch();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        dispatcher.beginDrain();
    }
}

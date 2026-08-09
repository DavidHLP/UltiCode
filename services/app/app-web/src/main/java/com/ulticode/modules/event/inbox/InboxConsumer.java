package com.ulticode.modules.event.inbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import java.util.UUID;
/**
 * Consumer for the {@code consumer_inbox} table (P6-INBOX-001).
 *
 * <p>Leases PENDING (or stale-PROCESSING) rows, dispatches them to registered handlers
 * keyed by event type, and marks PROCESSED or retries with backoff.
 *
 * <p>The {@code (consumer, event_id)} unique constraint on the table guarantees
 * exactly-once processing: a duplicate event insert is silently rejected by MySQL,
 * and the first row is processed normally.
 */
@Slf4j
@Component
public class InboxConsumer {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    private final ConsumerInboxMapper inboxMapper;
    private final String consumerName;
    private final String instanceId = UUID.randomUUID().toString();
    private final Map<String, Consumer<Map<String, Object>>> handlers =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Spring-managed default consumer for app-wide events that do not have a
     * more specific owner binding.
     */
    @Autowired
    public InboxConsumer(ConsumerInboxMapper inboxMapper) {
        this(inboxMapper, "App");
    }

    /**
     * Build a worker for a specific durable consumer binding.
     *
     * <p>Each binding gets its own database deduplication key and Redis group.
     * This lets Notification and Achievement independently retry the same
     * {@code SubmissionJudged} event.
     */
    public InboxConsumer(ConsumerInboxMapper inboxMapper, String consumerName) {
        this.inboxMapper = inboxMapper;
        this.consumerName = consumerName;
    }

    /**
     * Register a handler for a specific event type.
     */
    public void registerHandler(String eventType, Consumer<Map<String, Object>> handler) {
        handlers.put(eventType, handler);
    }

    @Scheduled(fixedDelayString = "${integration.inbox.consumer.interval-ms:2000}",
               initialDelayString = "5000")
    public int consume() {
        String leaseOwner = consumerName + ":" + instanceId;
        int leased = inboxMapper.claimLease(leaseOwner, consumerName, BATCH_SIZE);
        if (leased == 0) {
            return 0;
        }

        List<ConsumerInboxRecord> records = inboxMapper.selectLeased(leaseOwner, consumerName);
        int processed = 0;

        for (ConsumerInboxRecord record : records) {
            try {
                Consumer<Map<String, Object>> handler = handlers.get(record.getEventType());
                if (handler != null) {
                    handler.accept(record.getPayload());
                } else {
                    log.warn("No handler registered for event type {}, marking as processed",
                            record.getEventType());
                }
                if (inboxMapper.markProcessed(record.getId(), consumerName, leaseOwner) > 0) {
                    processed++;
                }
            } catch (Exception e) {
                log.error("Failed to process inbox event {} (type={}): {}",
                        record.getEventId(), record.getEventType(), e.getMessage(), e);
                inboxMapper.markFailed(
                        record.getId(),
                        consumerName,
                        leaseOwner,
                        truncate(e.getMessage(), 500),
                        MAX_ATTEMPTS);
            }
        }

        return processed;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

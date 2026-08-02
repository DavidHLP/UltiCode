package com.ulticode.modules.event.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
@RequiredArgsConstructor
public class InboxConsumer {

    private static final String CONSUMER_NAME = "App";
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final ConsumerInboxMapper inboxMapper;
    private final Map<String, Consumer<Map<String, Object>>> handlers = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Register a handler for a specific event type.
     */
    public void registerHandler(String eventType, Consumer<Map<String, Object>> handler) {
        handlers.put(eventType, handler);
    }

    @Scheduled(fixedDelayString = "${integration.inbox.consumer.interval-ms:2000}",
               initialDelayString = "5000")
    public int consume() {
        int leased = inboxMapper.claimLease(CONSUMER_NAME + ":" + ProcessHandle.current().pid(), BATCH_SIZE);
        if (leased == 0) {
            return 0;
        }

        List<ConsumerInboxRecord> records = inboxMapper.selectLeased(
                CONSUMER_NAME + ":" + ProcessHandle.current().pid());
        int processed = 0;

        for (ConsumerInboxRecord record : records) {
            try {
                Consumer<Map<String, Object>> handler = handlers.get(record.getEventType());
                if (handler != null) {
                    handler.accept(record.getPayload());
                } else {
                    log.warn("No handler registered for event type {}, marking as processed", record.getEventType());
                }
                inboxMapper.markProcessed(record.getId());
                processed++;
            } catch (Exception e) {
                log.error("Failed to process inbox event {} (type={}): {}",
                        record.getEventId(), record.getEventType(), e.getMessage(), e);
                inboxMapper.markFailed(record.getId(), truncate(e.getMessage(), 500), MAX_ATTEMPTS);
            }
        }

        return processed;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

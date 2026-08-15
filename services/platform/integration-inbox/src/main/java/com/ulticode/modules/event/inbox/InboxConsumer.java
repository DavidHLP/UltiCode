package com.ulticode.modules.event.inbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
/**
 * Consumer for the {@code consumer_inbox} table (P6-INBOX-001).
 *
 * <p>Leases PENDING (or stale-PROCESSING) rows, dispatches them to registered
 * handlers keyed by event type, and marks PROCESSED or retries with backoff.
 *
 * <p>Ordinary handlers and their terminal inbox transition run in one
 * transaction. Handlers registered with
 * {@link #registerHandlerOutsideTransaction(String, BiConsumer)} run outside
 * that transaction; only their PROCESSED transition is committed
 * transactionally after the handler returns.
 */
@Slf4j
public class InboxConsumer {

    private static final int BATCH_SIZE = 50;
    /** Keep the inbox retry horizon above the notification ledger's 10-minute reaper grace. */
    private static final int MAX_ATTEMPTS = 10;
    private static final long LEASE_HEARTBEAT_SECONDS = 10;

    private final ConsumerInboxMapper inboxMapper;
    private final String consumerName;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate leaseTransactionTemplate;
    private final String instanceId = UUID.randomUUID().toString();
    private final Map<String, HandlerRegistration> handlers =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record HandlerRegistration(
            BiConsumer<String, Map<String, Object>> handler,
            boolean transactional) {
    }

    /**
     * Test-friendly constructor for a consumer without a Spring transaction
     * manager.
     */
    public InboxConsumer(ConsumerInboxMapper inboxMapper) {
        this(inboxMapper, "App", null);
    }

    /**
     * Build a worker for a specific durable consumer binding.
     */
    public InboxConsumer(ConsumerInboxMapper inboxMapper, String consumerName) {
        this(inboxMapper, consumerName, null);
    }

    public InboxConsumer(ConsumerInboxMapper inboxMapper, String consumerName,
                         TransactionTemplate transactionTemplate) {
        this.inboxMapper = inboxMapper;
        this.consumerName = consumerName;
        this.transactionTemplate = transactionTemplate;
        if (transactionTemplate == null || transactionTemplate.getTransactionManager() == null) {
            this.leaseTransactionTemplate = null;
        } else {
            TransactionTemplate requiresNew = new TransactionTemplate(
                    transactionTemplate.getTransactionManager());
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.leaseTransactionTemplate = requiresNew;
        }
    }

    /**
     * Register a payload-only handler for a specific event type.
     */
    public void registerHandler(String eventType, Consumer<Map<String, Object>> handler) {
        registerHandlerWithEventId(eventType, (ignoredEventId, payload) -> handler.accept(payload));
    }

    /**
     * Register a handler that can verify the durable event identity.
     */
    public void registerHandlerWithEventId(
            String eventType, BiConsumer<String, Map<String, Object>> handler) {
        handlers.put(eventType, new HandlerRegistration(handler, true));
    }

    /**
     * Register a handler whose external I/O and durable side effects must not
     * run inside the inbox transaction. The PROCESSED transition is still
     * committed transactionally after the handler returns.
     */
    public void registerHandlerOutsideTransaction(
            String eventType, BiConsumer<String, Map<String, Object>> handler) {
        handlers.put(eventType, new HandlerRegistration(handler, false));
    }

    public int consume() {
        String leaseOwner = consumerName + ":" + instanceId;
        int leased = inboxMapper.claimLease(leaseOwner, consumerName, BATCH_SIZE);
        if (leased == 0) {
            return 0;
        }

        List<ConsumerInboxRecord> records = inboxMapper.selectLeased(leaseOwner, consumerName);
        int processed = 0;
        ScheduledExecutorService heartbeatExecutor = transactionTemplate == null
                ? null
                : Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "inbox-lease-heartbeat");
                    thread.setDaemon(true);
                    return thread;
                });
        try {
            for (ConsumerInboxRecord record : records) {
                Exception failure = null;
                ScheduledFuture<?> heartbeat = null;
                try {
                    if (renewLease(record, leaseOwner) == 0) {
                        throw new IllegalStateException("Inbox lease lost before handler started");
                    }
                    if (heartbeatExecutor != null) {
                        heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                                () -> renewLeaseSafely(record, leaseOwner),
                                LEASE_HEARTBEAT_SECONDS,
                                LEASE_HEARTBEAT_SECONDS,
                                TimeUnit.SECONDS);
                    }
                    if (transactionTemplate == null) {
                        processRecord(record, leaseOwner);
                    } else if (isOutsideTransactionHandler(record.getEventType())) {
                        processOutsideTransactionRecord(record, leaseOwner);
                    } else {
                        transactionTemplate.executeWithoutResult(
                                status -> processRecord(record, leaseOwner));
                    }
                } catch (Exception e) {
                    failure = e;
                } finally {
                    if (heartbeat != null) {
                        heartbeat.cancel(false);
                    }
                }

                if (failure == null) {
                    processed++;
                    continue;
                }

                String failureReason = safeFailureReason(failure);
                log.error("Failed to process inbox event {} (type={}): {}",
                        record.getEventId(), record.getEventType(), failureReason);
                try {
                    inboxMapper.markFailed(
                            record.getId(),
                            consumerName,
                            leaseOwner,
                            failureReason,
                            MAX_ATTEMPTS);
                } catch (Exception markFailure) {
                    log.error("Failed to record inbox failure for event {}: {}",
                            record.getEventId(), safeFailureReason(markFailure));
                }
            }
            return processed;
        } finally {
            if (heartbeatExecutor != null) {
                heartbeatExecutor.shutdownNow();
            }
        }
    }

    private void processRecord(ConsumerInboxRecord record, String leaseOwner) {
        HandlerRegistration registration = handlers.get(record.getEventType());
        if (registration != null) {
            registration.handler().accept(record.getEventId(), record.getPayload());
        } else {
            log.warn("No handler registered for event type {}, marking as processed",
                    record.getEventType());
        }

        markProcessed(record, leaseOwner);
    }

    private void processOutsideTransactionRecord(
            ConsumerInboxRecord record, String leaseOwner) {
        HandlerRegistration registration = handlers.get(record.getEventType());
        if (registration != null) {
            registration.handler().accept(record.getEventId(), record.getPayload());
        } else {
            log.warn("No handler registered for event type {}, marking as processed",
                    record.getEventType());
        }

        transactionTemplate.executeWithoutResult(
                status -> markProcessed(record, leaseOwner));
    }

    private boolean isOutsideTransactionHandler(String eventType) {
        HandlerRegistration registration = handlers.get(eventType);
        return registration != null && !registration.transactional();
    }

    private void markProcessed(ConsumerInboxRecord record, String leaseOwner) {
        if (inboxMapper.markProcessed(record.getId(), consumerName, leaseOwner) == 0) {
            throw new IllegalStateException("Inbox lease lost before processing completed");
        }
    }

    private int renewLease(ConsumerInboxRecord record, String leaseOwner) {
        return inLeaseTransaction(() -> inboxMapper.renewLease(
                record.getId(), consumerName, leaseOwner));
    }

    private void renewLeaseSafely(ConsumerInboxRecord record, String leaseOwner) {
        try {
            if (renewLease(record, leaseOwner) == 0) {
                log.warn("Inbox lease lost during processing for event {}", record.getEventId());
            }
        } catch (Exception e) {
            log.warn("Inbox lease heartbeat failed for event {}: {}",
                    record.getEventId(), safeFailureReason(e));
        }
    }

    private <T> T inLeaseTransaction(Supplier<T> action) {
        if (leaseTransactionTemplate == null) {
            return action.get();
        }
        return leaseTransactionTemplate.execute(status -> action.get());
    }


    private static String safeFailureReason(Exception failure) {
        if (failure == null) {
            return "UnknownFailure";
        }
        String name = failure.getClass().getSimpleName();
        return name == null || name.isBlank() ? "UnknownFailure" : name;
    }
}

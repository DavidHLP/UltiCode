package com.ulticode.common.outbox;

import com.ulticode.common.lifecycle.DrainGate;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared claim, publish, confirm, retry and shutdown mechanics for outboxes. */
public final class OutboxDispatcher<T> {

    public static final int BATCH_SIZE = 50;
    public static final int MAX_ATTEMPTS = 5;

    private static final Logger LOGGER = Logger.getLogger(OutboxDispatcher.class.getName());

    private final String claimOwner;
    private final Adapter<T> adapter;
    private final DrainGate drainGate = new DrainGate();

    public OutboxDispatcher(String claimOwnerPrefix, Adapter<T> adapter) {
        this.claimOwner = claimOwnerPrefix + "-" + UUID.randomUUID();
        this.adapter = adapter;
    }

    /** Run one bounded claim/publish/confirm cycle. */
    public int dispatch() {
        if (!drainGate.tryEnter()) {
            return 0;
        }
        try {
            adapter.reclaimStaleClaimed();
            if (adapter.claimPending(claimOwner, BATCH_SIZE) == 0) {
                return 0;
            }
            List<T> records = adapter.selectClaimed(claimOwner);
            if (records == null || records.isEmpty()) {
                return 0;
            }

            int published = 0;
            for (T record : records) {
                try {
                    String publicationId = adapter.publish(record);
                    if (adapter.markDelivered(record, claimOwner, publicationId) > 0) {
                        published++;
                    }
                } catch (Exception exception) {
                    LOGGER.log(Level.WARNING,
                            "Failed to dispatch outbox " + adapter.recordId(record), exception);
                    adapter.markFailed(
                            record,
                            claimOwner,
                            truncate(exception.getMessage(), 500),
                            MAX_ATTEMPTS);
                }
            }
            return published;
        } finally {
            drainGate.leave();
        }
    }

    /** Refuse new cycles while allowing the current bounded cycle to finish. */
    public void beginDrain() {
        drainGate.beginDrain();
    }

    public interface Adapter<T> {

        void reclaimStaleClaimed();

        int claimPending(String claimOwner, int limit);

        List<T> selectClaimed(String claimOwner);

        /** Return an optional publication id, such as a Redis stream id. */
        String publish(T record) throws Exception;

        int markDelivered(T record, String claimOwner, String publicationId);

        int markFailed(T record, String claimOwner, String error, int maxAttempts);

        String recordId(T record);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

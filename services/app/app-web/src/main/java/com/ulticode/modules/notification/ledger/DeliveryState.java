package com.ulticode.modules.notification.ledger;

/**
 * Delivery state for a single {@code (intent_id, channel_id)} row in the
 * {@code notification_delivery_ledger} table.
 *
 * <p>State transitions:
 * <ul>
 *   <li>{@link #CLAIMED} — set by {@code tryClaim()}, with a dispatcher
 *       {@code claim_owner} and lease timestamp. If the process crashes before
 *       a terminal update, the reaper fences and reclaims the stale lease.</li>
 *   <li>{@link #DELIVERED} — the channel's {@code send()} returned without
 *       throwing. Terminal.</li>
 *   <li>{@link #SKIPPED} — the channel's {@code supports(intent)} returned false
 *       (e.g. {@code FollowReceivedIntent} on Email channel). Terminal.</li>
 *   <li>{@link #FAILED} — the channel's {@code send()} threw. The row may be
 *       retried after the backoff until the bounded reclaim-attempt limit;
 *       once exhausted, it is terminal.</li>
 * </ul>
 *
 * <p>Stored as a {@code VARCHAR(16)} (not a MySQL ENUM) so future states do not
 * require a schema migration; the column comment in the Flyway script enumerates
 * the current set.
 */
public enum DeliveryState {
    CLAIMED,
    DELIVERED,
    SKIPPED,
    FAILED
}

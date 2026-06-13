package com.ulticode.modules.notification.ledger;

/**
 * Delivery state for a single {@code (intent_id, channel_id)} row in the
 * {@code notification_delivery_ledger} table.
 *
 * <p>State transitions:
 * <ul>
 *   <li>{@link #CLAIMED} — set by {@code tryClaim()}, the initial state right
 *       after the dispatcher has reserved the slot. If the process crashes
 *       between CLAIMED and DELIVERED/FAILED, the row remains CLAIMED; a future
 *       reaper (or this dispatcher on retry) can either re-attempt or
 *       transition to FAILED.</li>
 *   <li>{@link #DELIVERED} — the channel's {@code send()} returned without
 *       throwing. Terminal.</li>
 *   <li>{@link #SKIPPED} — the channel's {@code supports(intent)} returned false
 *       (e.g. {@code FollowReceivedIntent} on Email channel). Terminal.</li>
 *   <li>{@link #FAILED} — the channel's {@code send()} threw. {@code failure_reason}
 *       carries the truncated exception message. Terminal. No auto-retry from
 *       this ledger — durable retry is a separate outbox path (ADR-004 §2.7).</li>
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

package com.ulticode.modules.notification.channel;

import com.ulticode.modules.notification.intent.NotificationIntent;

/**
 * Per-channel projection port (ADR-004 §2.2).
 *
 * <p>Replaces the legacy "channel list with generic envelope" pattern. Each
 * implementation is a {@code @Component} that:
 * <ol>
 *   <li>Declares its {@link #channelId()} — used as the ledger partition key
 *       (see {@code notification_delivery_ledger.channel_id}). Must be one of
 *       {@code "in_app"} / {@code "email"} / {@code "websocket"} for the
 *       canonical channels; new channels may pick any {@code snake_case}
 *       identifier as long as it is unique and stable across deploys.</li>
 *   <li>Implements {@link #supports(NotificationIntent)} — the dispatcher
 *       <b>must</b> call this for every intent; channels that cannot project
 *       a given intent return {@code false} and the dispatcher records a
 *       {@code SKIPPED} ledger row.</li>
 *   <li>Implements {@link #send(NotificationIntent)} — failure throws
 *       {@link com.ulticode.common.exception.BusinessException} or a
 *       runtime exception; the dispatcher catches and records {@code FAILED}
 *       in the ledger but does not rethrow (failure isolation, ADR-004 §2.3).</li>
 * </ol>
 *
 * <p>Implementations should be stateless and thread-safe: a single bean is
 * shared across the dispatcher fan-out and the optional outbox retry path.
 * The dispatcher owns claim, terminal-state persistence, retry policy, and
 * failure isolation; channel adapters must not write the ledger directly.
 */
public interface NotificationChannel {

    /**
     * Channel identifier; persisted as {@code notification_delivery_ledger.channel_id}.
     * Must be unique across all beans implementing this interface.
     */
    String channelId();

    /**
     * Whether this channel can project the given intent. The dispatcher calls
     * this for every registered channel; channels that want the dispatcher to
     * record a {@code SKIPPED} ledger row rather than no row at all should
     * return {@code false} (or throw from {@code supports}, but throwing is
     * not the convention — boolean return is preferred for static capability
     * checks).
     */
    boolean supports(NotificationIntent intent);

    /**
     * Project and deliver the intent. Throwing is the signal that delivery
     * failed; the dispatcher catches the exception, records a {@code FAILED}
     * ledger row with the (truncated) message, and continues with the next
     * channel. Implementations <b>must not</b> log the raw stack trace as
     * {@code error} for transient infrastructure errors (e.g. SMTP 421)
     * — use {@code warn} and let the metric + ledger surface the failure.
     */
    void send(NotificationIntent intent);
}

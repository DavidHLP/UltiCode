package com.ulticode.modules.notification.dispatcher;

import com.ulticode.modules.notification.channel.NotificationChannel;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.ledger.DeliveryState;
import com.ulticode.modules.notification.ledger.entity.NotificationDeliveryLedger;
import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Central fan-out for {@link NotificationIntent} (ADR-004 §2.3).
 *
 * <p>Algorithm per {@link #dispatch(NotificationIntent)}:
 * <ol>
 *   <li><b>Category preference gate</b> — look up the recipient's
 *       {@code NotificationPreference}. If the row exists and the relevant
 *       boolean is {@code false}, return immediately; the intent is dropped
 *       (no ledger row — that is the "user opted out" path). A missing row
 *       uses the DDL defaults (communication=true, marketing=false,
 *       security=true, system=true).</li>
 *   <li>For each registered channel, in bean-order:
 *     <ol type="a">
 *       <li>{@code tryClaim(intentId, channelId, claimOwner)} — atomic
 *           ledger INSERT/CAS. Returns 0 when another owner holds the
 *           lease, the row is terminal, or retry backoff applies.</li>
 *       <li>{@code channel.supports(intent)} — boolean capability check.
 *           If false → mark {@code SKIPPED} in the ledger, continue.</li>
 *       <li>{@code channel.send(intent)} — actual delivery. On success
 *           mark {@code DELIVERED}; on exception mark {@code FAILED} with a
 *           bounded class-only reason, increment the failure counter, and
 *           continue to the next channel (failure isolation).</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * <p>Invariants:
 * <ul>
 *   <li>The synchronous dispatcher never rethrows channel exceptions. The
 *       durable inbox path propagates a sanitized failure so the event remains
 *       retryable when delivery state cannot be confirmed.</li>
 *   <li>Every delivery attempt uses a unique lease owner. Terminal updates
 *       are fenced by that owner, so a stale worker cannot overwrite a newer
 *       claim.</li>
 *   <li>Durable delivery is at-least-once. A channel send and its ledger
 *       confirmation are separate operations; this seam guarantees stable
 *       ledger state and replay, not exactly-once behavior from SMTP or
 *       WebSocket transports.</li>
 *   <li>Returns {@code void}; callers that need per-channel outcomes query the
 *       ledger rather than relying on an in-memory result.</li>
 * </ul>
 */
@Slf4j
@Component
public class NotificationDispatcher {

    /** Soft cap for the persisted {@code failure_reason} column. */
    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    private final List<NotificationChannel> channels;
    private final NotificationDeliveryLedgerMapper ledgerMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final MeterRegistry meterRegistry;

    public NotificationDispatcher(List<NotificationChannel> channels,
                                  NotificationDeliveryLedgerMapper ledgerMapper,
                                  NotificationPreferenceMapper preferenceMapper,
                                  MeterRegistry meterRegistry) {
        this.channels = channels;
        this.ledgerMapper = ledgerMapper;
        this.preferenceMapper = preferenceMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Dispatch the given intent to all applicable channels without propagating
     * channel failures. This preserves the legacy synchronous caller contract.
     *
     * @param intent the typed intent (sealed interface, see
     *               {@link NotificationIntent}); must be non-null
     */
    public void dispatch(NotificationIntent intent) {
        dispatchInternal(intent, false);
    }

    /**
     * Dispatch an intent for a durable inbox consumer.
     *
     * <p>All channels are still attempted, but a channel failure or an
     * inability to record a successful delivery is propagated after the
     * fan-out so the inbox can retain the event for retry.
     */
    public void dispatchForDurableRetry(NotificationIntent intent) {
        Exception failure = dispatchInternal(intent, true);
        if (failure != null) {
            throw new IllegalStateException(
                    "Notification delivery failed for intent " + intent.intentId()
                            + " (" + safeFailureReason(failure) + ")");
        }
    }

    private Exception dispatchInternal(NotificationIntent intent, boolean propagateLedgerFailures) {
        if (!isCategoryEnabled(intent)) {
            log.debug("Notification suppressed by preference: user={} category={}",
                    intent.userId(), intent.category());
            incrementCounter("notification.dispatch.suppressed",
                    "category", intent.category().name());
            return null;
        }

        String claimOwner = "notification-dispatcher-" + UUID.randomUUID();
        String intentType = intent.wireType();
        Exception firstFailure = null;
        for (NotificationChannel channel : channels) {
            try {
                int claimed = ledgerMapper.tryClaim(
                        intent.intentId(),
                        channel.channelId(),
                        intent.userId(),
                        intentType,
                        claimOwner);
                if (claimed == 0) {
                    if (propagateLedgerFailures) {
                        NotificationDeliveryLedger existing = ledgerMapper.findByIntentAndChannel(
                                intent.intentId(), channel.channelId());
                        if (existing != null
                                && existing.getDeliveryState() == DeliveryState.CLAIMED) {
                            if (firstFailure == null) {
                                firstFailure = new IllegalStateException(
                                        "Notification ledger claim is already in flight for intent "
                                                + intent.intentId() + " channel "
                                                + channel.channelId());
                            }
                            continue;
                        }
                        if (existing == null
                                || (existing.getDeliveryState() != DeliveryState.DELIVERED
                                && existing.getDeliveryState() != DeliveryState.SKIPPED)) {
                            if (firstFailure == null) {
                                String state = existing == null
                                        ? "missing"
                                        : existing.getDeliveryState().name();
                                firstFailure = new IllegalStateException(
                                        "Notification ledger claim was not available for intent "
                                                + intent.intentId() + " channel "
                                                + channel.channelId() + " (state=" + state + ")");
                            }
                            continue;
                        }
                    }
                    log.debug("intent {} channel {} already delivered, skip",
                            intent.intentId(), channel.channelId());
                    continue;
                }

                if (!channel.supports(intent)) {
                    Exception ledgerFailure = markSkipped(
                            intent, channel, claimOwner, propagateLedgerFailures);
                    if (ledgerFailure != null && firstFailure == null) {
                        firstFailure = ledgerFailure;
                    }
                    continue;
                }
                channel.send(intent);
                Exception deliveryFailure = markDelivered(
                        intent, channel, intentType, claimOwner, propagateLedgerFailures);
                if (deliveryFailure != null && firstFailure == null) {
                    firstFailure = deliveryFailure;
                }
            } catch (Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
                // Failure isolation (ADR-004 §2.3): record the failure and let
                // the loop continue. markFailed wraps its own ledger/counter/log
                // calls so a broken ledger cannot escape here.
                markFailed(intent, channel, intentType, claimOwner, e);
            }
        }
        return firstFailure;
    }
    /**
     * Record a successful delivery.
     *
     * <p>A synchronous caller keeps the legacy failure-isolation contract.
     * The durable inbox path receives a ledger failure so it can retry the
     * event instead of acknowledging it as processed. A failed confirmation
     * is never converted into FAILED by this method: only the lease owner may
     * finalize a row, and a stale owner must not overwrite a newer attempt.
     */
    private Exception markDelivered(NotificationIntent intent, NotificationChannel channel,
                                    String intentType, String claimOwner,
                                    boolean propagateLedgerFailures) {
        try {
            int updated = ledgerMapper.markDelivered(
                    intent.intentId(), channel.channelId(), claimOwner);
            if (updated == 0) {
                NotificationDeliveryLedger existing = ledgerMapper.findByIntentAndChannel(
                        intent.intentId(), channel.channelId());
                if (existing == null || existing.getDeliveryState() != DeliveryState.DELIVERED) {
                    Exception failure = new IllegalStateException(
                            "Notification delivery state was not confirmed for intent "
                                    + intent.intentId() + " channel " + channel.channelId());
                    log.warn("delivery confirmation lost lease for intent {} channel {}",
                            intent.intentId(), channel.channelId());
                    return propagateLedgerFailures ? failure : null;
                }
            }
        } catch (Exception e) {
            log.warn("post-delivery ledger update failed for intent {} channel {}: {}",
                    intent.intentId(), channel.channelId(), safeFailureReason(e));
            return propagateLedgerFailures ? e : null;
        }

        try {
            meterRegistry.counter("notification.dispatch.delivered",
                    "channel", channel.channelId(),
                    "intent", intentType).increment();
        } catch (Exception e) {
            log.warn("post-delivery counter update failed for intent {} channel {}: {}",
                    intent.intentId(), channel.channelId(), safeFailureReason(e));
        }
        return null;
    }

    private Exception markSkipped(NotificationIntent intent, NotificationChannel channel,
                                  String claimOwner, boolean propagateLedgerFailures) {
        try {
            int updated = ledgerMapper.markSkipped(
                    intent.intentId(), channel.channelId(), claimOwner);
            if (updated == 0) {
                NotificationDeliveryLedger existing = ledgerMapper.findByIntentAndChannel(
                        intent.intentId(), channel.channelId());
                if (existing == null || existing.getDeliveryState() != DeliveryState.SKIPPED) {
                    Exception failure = new IllegalStateException(
                            "Notification skip state was not confirmed for intent "
                                    + intent.intentId() + " channel " + channel.channelId());
                    log.warn("skip confirmation lost lease for intent {} channel {}",
                            intent.intentId(), channel.channelId());
                    return propagateLedgerFailures ? failure : null;
                }
            }
        } catch (Exception e) {
            log.warn("notification skip ledger update failed for intent {} channel {}: {}",
                    intent.intentId(), channel.channelId(), safeFailureReason(e));
            return propagateLedgerFailures ? e : null;
        }
        return null;
    }

    /**
     * Record a failed delivery. Wrapped so a broken ledger/meter (the likely
     * cause of the original failure) cannot escape and poison the next channel
     * (ADR-004 §2.3 failure isolation).
     */
    private void markFailed(NotificationIntent intent, NotificationChannel channel,
                            String intentType, String claimOwner, Exception cause) {
        try {
            String reason = safeFailureReason(cause);
            ledgerMapper.markFailed(
                    intent.intentId(), channel.channelId(), reason, claimOwner);
            meterRegistry.counter("notification.dispatch.failure",
                    "channel", channel.channelId(),
                    "intent", intentType).increment();
            log.warn("channel {} failed for intent {}: {}",
                    channel.channelId(), intent.intentId(), reason);
        } catch (Exception secondary) {
            log.warn("channel {} failed for intent {} (failure recording also failed: {})",
                    channel.channelId(), intent.intentId(), safeFailureReason(secondary));
        }
    }

    private static String safeFailureReason(Exception failure) {
        if (failure == null) {
            return "UnknownFailure";
        }
        String name = failure.getClass().getSimpleName();
        return name == null || name.isBlank() ? "UnknownFailure" : truncate(name);
    }

    /**
     * Increment a counter, swallowing a meter-registry failure so a metrics
     * hiccup never breaks dispatch (ADR-004 §2.3 failure isolation).
     */
    private void incrementCounter(String name, String... tags) {
        try {
            meterRegistry.counter(name, tags).increment();
        } catch (Exception e) {
            log.debug("counter {} increment failed: {}", name, safeFailureReason(e));
        }
    }

    /**
     * Look up the recipient preference row and apply the DDL defaults:
     * missing row → marketing=false, others=true. Admin broadcast applies
     * the same semantics in {@code AdminNotificationServiceImpl}.
     */
    private boolean isCategoryEnabled(NotificationIntent intent) {
        return preferenceMapper.findByUserId(intent.userId())
                .map(p -> switch (intent.category()) {
                    case COMMUNICATION -> Boolean.TRUE.equals(p.getCommunication());
                    case MARKETING    -> Boolean.TRUE.equals(p.getMarketing());
                    case SECURITY     -> Boolean.TRUE.equals(p.getSecurity());
                    case SYSTEM       -> Boolean.TRUE.equals(p.getSystemEnabled());
                })
                .orElseGet(() -> intent.category() != NotificationCategory.MARKETING);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= FAILURE_REASON_MAX_LENGTH
                ? s
                : s.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}

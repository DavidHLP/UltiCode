package com.ulticode.modules.notification.dispatcher;

import com.ulticode.modules.notification.channel.NotificationChannel;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.ledger.DeliveryState;
import com.ulticode.modules.notification.ledger.entity.NotificationDeliveryLedger;
import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
 *   <li>For each registered channel, in bean-order (Spring
 *       {@code @Order} or fallback to registration order):
 *     <ol type="a">
 *       <li>{@code tryClaim(intentId, channelId)} — atomic ledger INSERT.
 *           Returns 0 → already delivered (multi-replica or retry), skip
 *           silently. Returns 1 → this caller owns the slot.</li>
 *       <li>{@code channel.supports(intent)} — boolean capability check.
 *           If false → mark {@code SKIPPED} in the ledger, continue.</li>
 *       <li>{@code channel.send(intent)} — actual delivery. On success
 *           mark {@code DELIVERED}; on exception mark {@code FAILED} with
 *           the truncated message, increment the failure counter, continue
 *           to the next channel (failure isolation).</li>
 *     </ol></li>
 * </ol>
 *
 * <p>Invariants:
 * <ul>
 *   <li>The dispatcher never rethrows channel exceptions. The caller (which
 *       is typically a transaction-bound service) sees a successful return
 *       even if every channel failed; the failure is recorded in the ledger
 *       and surfaced via the {@code notification.dispatch.failure} counter.</li>
 *   <li>The dispatcher is stateless and safe for concurrent use. The ledger
 *       provides the cross-process serialization; there is no in-memory
 *       cache that would be lost on pm2 reload (ADR-004 F9).</li>
 *   <li>Returns {@code void}. Callers that need to know the per-channel
 *       outcome should query the ledger; the dispatcher does not propagate
 *       success/failure upward.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    /** Soft cap for the persisted {@code failure_reason} column. */
    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    private final List<NotificationChannel> channels;
    private final NotificationDeliveryLedgerMapper ledgerMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final MeterRegistry meterRegistry;

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
                    "Notification delivery failed for intent " + intent.intentId(), failure);
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

        String intentType = intent.wireType();
        Exception firstFailure = null;
        for (NotificationChannel channel : channels) {
            try {
                int claimed = ledgerMapper.tryClaim(
                        intent.intentId(),
                        channel.channelId(),
                        intent.userId(),
                        intentType);
                if (claimed == 0) {
                    if (propagateLedgerFailures) {
                        NotificationDeliveryLedger existing =
                                ledgerMapper.findByIntentAndChannel(
                                        intent.intentId(), channel.channelId());
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
                    ledgerMapper.markSkipped(intent.intentId(), channel.channelId());
                    continue;
                }
                channel.send(intent);
                Exception ledgerFailure = markDelivered(
                        intent, channel, intentType, propagateLedgerFailures);
                if (ledgerFailure != null && firstFailure == null) {
                    firstFailure = ledgerFailure;
                }
            } catch (Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
                // Failure isolation (ADR-004 §2.3): record the failure and let
                // the loop continue. markFailed wraps its own ledger/counter/log
                // calls so a broken ledger cannot escape here.
                markFailed(intent, channel, intentType, e);
            }
        }
        return firstFailure;
    }

    /**
     * Record a successful delivery.
     *
     * <p>A synchronous caller keeps the legacy failure-isolation contract.
     * The durable inbox path receives a ledger failure so it can retry the
     * event instead of acknowledging it as processed. Metrics remain
     * best-effort in both modes.
     */
    private Exception markDelivered(NotificationIntent intent, NotificationChannel channel,
                                    String intentType, boolean propagateLedgerFailures) {
        try {
            int updated = ledgerMapper.markDelivered(intent.intentId(), channel.channelId());
            if (updated == 0 && propagateLedgerFailures) {
                NotificationDeliveryLedger existing = ledgerMapper.findByIntentAndChannel(
                        intent.intentId(), channel.channelId());
                if (existing == null || existing.getDeliveryState() != DeliveryState.DELIVERED) {
                    Exception failure = new IllegalStateException(
                            "Notification delivery state was not confirmed for intent "
                                    + intent.intentId() + " channel " + channel.channelId());
                    recoverClaim(intent, channel, failure);
                    return failure;
                }
            }
        } catch (Exception e) {
            log.warn("post-delivery ledger update failed for intent {} channel {}: {}",
                    intent.intentId(), channel.channelId(), e.getMessage());
            recoverClaim(intent, channel, e);
            return propagateLedgerFailures ? e : null;
        }

        try {
            meterRegistry.counter("notification.dispatch.delivered",
                    "channel", channel.channelId(),
                    "intent", intentType).increment();
        } catch (Exception e) {
            log.warn("post-delivery counter update failed for intent {} channel {}: {}",
                    intent.intentId(), channel.channelId(), e.getMessage());
        }
        return null;
    }

    private void recoverClaim(NotificationIntent intent, NotificationChannel channel,
                               Exception cause) {
        try {
            ledgerMapper.markFailed(
                    intent.intentId(),
                    channel.channelId(),
                    truncate("post-delivery ledger update failed: " + cause.getMessage()));
        } catch (Exception recoveryFailure) {
            log.warn("failed to recover CLAIMED ledger row for intent {} channel {}: {}",
                    intent.intentId(), channel.channelId(), recoveryFailure.getMessage());
        }
    }

    /**
     * Record a failed delivery. Wrapped so a broken ledger/meter (the likely
     * cause of the original failure) cannot escape and poison the next channel
     * (ADR-004 §2.3 failure isolation).
     */
    private void markFailed(NotificationIntent intent, NotificationChannel channel,
                            String intentType, Exception cause) {
        try {
            String reason = truncate(cause.getClass().getSimpleName() + ": "
                    + (cause.getMessage() == null ? "" : cause.getMessage()));
            ledgerMapper.markFailed(intent.intentId(), channel.channelId(), reason);
            meterRegistry.counter("notification.dispatch.failure",
                    "channel", channel.channelId(),
                    "intent", intentType).increment();
            log.warn("channel {} failed for intent {}: {}",
                    channel.channelId(), intent.intentId(), reason);
        } catch (Exception secondary) {
            // The failure-recording path itself failed (e.g. ledger down).
            // Log both failures and continue — one channel failure must not cascade.
            log.warn("channel {} failed for intent {} (cause={}) AND recording the failure failed: {}",
                    channel.channelId(), intent.intentId(), cause.getMessage(), secondary.getMessage());
        }
    }

    /**
     * Increment a counter, swallowing a meter-registry failure so a metrics
     * hiccup never breaks dispatch (ADR-004 §2.3 failure isolation).
     */
    private void incrementCounter(String name, String... tags) {
        try {
            meterRegistry.counter(name, tags).increment();
        } catch (Exception e) {
            log.debug("counter {} increment failed: {}", name, e.getMessage());
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

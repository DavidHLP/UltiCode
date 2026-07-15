package com.ulticode.modules.notification.dispatcher;

import com.ulticode.modules.notification.channel.NotificationChannel;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.NotificationIntent;
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
     * Dispatch the given intent to all applicable channels.
     *
     * @param intent the typed intent (sealed interface, see
     *               {@link NotificationIntent}); must be non-null
     */
    public void dispatch(NotificationIntent intent) {
        if (!isCategoryEnabled(intent)) {
            log.debug("Notification suppressed by preference: user={} category={}",
                    intent.userId(), intent.category());
            meterRegistry.counter("notification.dispatch.suppressed",
                    "category", intent.category().name()).increment();
            return;
        }

        String intentType = intent.getClass().getSimpleName();
        for (NotificationChannel channel : channels) {
            try {
                int claimed = ledgerMapper.tryClaim(
                        intent.intentId(),
                        channel.channelId(),
                        intent.userId(),
                        intentType);
                if (claimed == 0) {
                    log.debug("intent {} channel {} already delivered, skip",
                            intent.intentId(), channel.channelId());
                    continue;
                }

                if (!channel.supports(intent)) {
                    ledgerMapper.markSkipped(intent.intentId(), channel.channelId());
                    continue;
                }

                channel.send(intent);
                ledgerMapper.markDelivered(intent.intentId(), channel.channelId());
                meterRegistry.counter("notification.dispatch.delivered",
                        "channel", channel.channelId(),
                        "intent", intentType).increment();

            } catch (Exception e) {
                String reason = truncate(e.getClass().getSimpleName() + ": "
                        + (e.getMessage() == null ? "" : e.getMessage()));
                ledgerMapper.markFailed(intent.intentId(), channel.channelId(), reason);
                meterRegistry.counter("notification.dispatch.failure",
                        "channel", channel.channelId(),
                        "intent", intentType).increment();
                log.warn("channel {} failed for intent {}: {}",
                        channel.channelId(), intent.intentId(), reason);
                // Do not rethrow — failure isolation (ADR-004 §2.3).
            }
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

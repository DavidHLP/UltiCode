package com.ulticode.modules.notification.ledger.reaper;

import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reaps {@code CLAIMED} rows that have been stuck for more than 10 minutes
 * (ADR-004 M4d-1 finding #4). Without this reaper, a dispatcher that
 * claimed a row but died before transitioning it would permanently block
 * future dispatches for the same {@code (intent_id, channel_id)} pair,
 * because {@code tryClaim}'s {@code ON DUPLICATE KEY UPDATE id=id} returns
 * 0 for the existing stuck row and the dispatcher treats 0 as
 * "already delivered, skip".
 *
 * <p>Cadence: every 5 minutes. With a 10-minute grace period, a stuck
 * row is usually fixed within 1-2 reaper cycles.
 *
 * <p>Operation:
 * <ol>
 *   <li>{@code reapStaleClaimed} transitions stuck rows to {@code FAILED}
 *       with a clear {@code failure_reason}.</li>
 *   <li>The counter {@code notification.ledger.reaper.reaped} exposes the
 *       reaped count for ops dashboards. A non-zero value indicates the
 *       dispatcher (or one of the channels) crashed mid-delivery.</li>
 * </ol>
 *
 * <p>This is a minimal stop-gap; the durable retry path (ADR-007) is the
 * long-term answer. For now, transitioning the stuck row to {@code FAILED}
 * is safer than leaving it as {@code CLAIMED} forever — a future
 * outbox-style retry can still re-attempt the original intent via
 * {@code NotificationDispatcher.dispatch(intent)}, which will see the
 * existing {@code FAILED} row, not skip it (the tryClaim is keyed on
 * {@code (intent_id, channel_id)}; the existing row's state is
 * informational).
 *
 * <p>Reference: wiki/concepts/notification-dispatch-and-preferences.md §2.7 + M4d-1
 * finding #4.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationLedgerReaper {

    private final NotificationDeliveryLedgerMapper ledgerMapper;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${ulticode.notification.ledger.reaper-interval-ms:300000}",
            initialDelayString = "${ulticode.notification.ledger.reaper-initial-delay-ms:60000}")
    public void reap() {
        try {
            int reaped = ledgerMapper.reapStaleClaimed();
            if (reaped > 0) {
                log.warn("Reaper transitioned {} stuck CLAIMED rows to FAILED "
                        + "(> 10min since claim — likely dispatcher or channel JVM crash)",
                        reaped);
                meterRegistry.counter("notification.ledger.reaper.reaped").increment(reaped);
            }
        } catch (Exception e) {
            // Reaper failures must not propagate — the scheduled task would
            // log the exception anyway, but a noisy log every 5 minutes
            // is worse than a single warn.
            log.warn("NotificationLedgerReaper.reap failed: {}", e.getMessage());
        }
    }
}

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
 * because an existing {@code CLAIMED} row would otherwise make
 * {@code tryClaim} return 0 and be treated as already delivered.
 *
 * <p>Cadence: every 5 minutes. With a 10-minute grace period, a stuck
 * row is usually fixed within 1-2 reaper cycles.
 *
 * <p>Operation:
 * <ol>
 *   <li>{@code reapStaleClaimed} transitions stuck rows to {@code FAILED}
 *       with a clear {@code failure_reason}.</li>
 *   <li>{@code reclaimFailedLegacy} clears the retry marker for eligible
 *       legacy rows; the next synchronous dispatch calls {@code tryClaim} and
 *       owns the new lease.</li>
 *   <li>Durable submission intents are retried by their owning inbox
 *       consumer.</li>
 *   <li>The counters {@code notification.ledger.reaper.reaped} and
 *       {@code notification.ledger.reaper.reclaimed} expose both paths.</li>
 * </ol>
 *
 * <p>Durable submission rows are excluded from the global FAILED reclaim so
 * the inbox consumer remains the sole owner of their retry lease.
 *
 * <p>Reference: notification/ledger/reaper/NotificationLedgerReaper + the
 * (intent_id, channel_id) UNIQUE index in
 * V20260613120000__Create_Notification_Delivery_Ledger.sql.
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
            int reclaimed = ledgerMapper.reclaimFailedLegacy();
            if (reclaimed > 0) {
                log.info("Reaper marked {} legacy FAILED rows eligible for retry "
                        + "(bounded at 5 attempts)", reclaimed);
                meterRegistry.counter("notification.ledger.reaper.reclaimed").increment(reclaimed);
            }

            // Durable inbox retries and legacy synchronous dispatches call
            // tryClaim to acquire their own lease; the reaper never owns it.
        } catch (Exception e) {
            log.warn("NotificationLedgerReaper.reap failed: {}", e.getMessage());
        }
    }
}

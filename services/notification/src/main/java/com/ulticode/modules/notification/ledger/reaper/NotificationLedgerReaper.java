package com.ulticode.modules.notification.ledger.reaper;

import com.ulticode.common.lifecycle.DrainGate;
import com.ulticode.modules.notification.ledger.DeliveryAttemptCoordinator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reaps {@code CLAIMED} rows that have been stuck for more than 10 minutes
 * (ADR-004 M4d-1 finding #4). Without this reaper, a dispatcher that
 * claimed a row but died before transitioning it would permanently block
 * future dispatches for the same {@code (intent_id, channel_id)} pair,
 * because the delivery-attempt coordinator would classify the existing
 * {@code CLAIMED} row as in flight.
 *
 * <p>Cadence: every 5 minutes. With a 10-minute grace period, a stuck
 * row is usually fixed within 1-2 reaper cycles.
 *
 * <p>Operation:
 * <ol>
 *   <li>The delivery-attempt coordinator's stale-claim operation transitions
 *       stale claims to {@code FAILED} with a clear {@code failure_reason}.</li>
 *   <li>FAILED rows are reclaimed by their owning dispatcher or inbox
 *       consumer through the delivery-attempt coordinator after the bounded
 *       backoff; the reaper does not mutate their retry clock.</li>
 *   <li>The counter {@code notification.ledger.reaper.reaped} exposes the
 *       stale-lease path.</li>
 * </ol>
 *
 * <p>Reference: notification/ledger/reaper/NotificationLedgerReaper + the
 * (intent_id, channel_id) UNIQUE index in
 * V20260613120000__Create_Notification_Delivery_Ledger.sql.
 *
 * <p>Runtime role (NOTIFY-004): this reaper is part of the App Notification
 * Delivery worker. It is registered only while
 * {@code ulticode.notification.worker.enabled} is true (default; the
 * {@code worker} profile sets it explicitly, the {@code api} profile turns it
 * off). It mutates stale lease rows through the delivery-attempt coordinator,
 * so multiple delivery replicas may run it safely (the reaper UPDATE is
 * idempotent under the state/lease predicate).
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ulticode.notification.worker.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationLedgerReaper {

    private final DeliveryAttemptCoordinator deliveryAttemptCoordinator;
    private final MeterRegistry meterRegistry;
    private final DrainGate drainGate = new DrainGate();

    @Scheduled(fixedDelayString = "${ulticode.notification.ledger.reaper-interval-ms:300000}",
            initialDelayString = "${ulticode.notification.ledger.reaper-initial-delay-ms:60000}")
    public void reap() {
        if (!drainGate.tryEnter()) {
            return;
        }
        try {
            DeliveryAttemptCoordinator.ReapResult result =
                    deliveryAttemptCoordinator.reapStaleClaims();
            if (result.hasReapedRows()) {
                log.warn("Reaper transitioned {} stuck CLAIMED rows to FAILED "
                        + "(> 10min since claim — likely dispatcher or channel JVM crash)",
                        result.reaped());
                meterRegistry.counter("notification.ledger.reaper.reaped")
                        .increment(result.reaped());
            }

            // Failed rows retain their backoff timestamp. The dispatcher owns
            // the next claim, including durable inbox retries.
        } catch (Exception e) {
            log.warn("NotificationLedgerReaper.reap failed: {}",
                    e.getClass().getSimpleName());
        } finally {
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }
}

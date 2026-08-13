package com.ulticode.modules.notification.ledger.reaper;

import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NOTIFY-004: the delivery worker reaper's retry/failure metric contract.
 *
 * <p>A reaped stale lease must be observable through
 * {@code notification.ledger.reaper.reaped}, and a broken ledger must never
 * escape the scheduled {@link NotificationLedgerReaper#reap()} (failure
 * containment so one scheduler tick cannot poison the worker).
 */
@ExtendWith(MockitoExtension.class)
class NotificationLedgerReaperTest {

    @Mock
    private NotificationDeliveryLedgerMapper ledgerMapper;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Counter reapedCounter;

    @Test
    @DisplayName("reaped stale rows increment the reaper counter")
    void reapedRowsIncrementCounter() {
        when(meterRegistry.counter("notification.ledger.reaper.reaped")).thenReturn(reapedCounter);
        when(ledgerMapper.reapStaleClaimed()).thenReturn(3);

        new NotificationLedgerReaper(ledgerMapper, meterRegistry).reap();

        verify(reapedCounter).increment(3);
    }

    @Test
    @DisplayName("no stale rows leaves the counter untouched")
    void noStaleRowsKeepsCounterSilent() {
        when(ledgerMapper.reapStaleClaimed()).thenReturn(0);

        new NotificationLedgerReaper(ledgerMapper, meterRegistry).reap();

        verify(meterRegistry, never()).counter(anyString());
    }

    @Test
    @DisplayName("a ledger failure is contained and does not poison the worker")
    void ledgerFailureIsContained() {
        when(ledgerMapper.reapStaleClaimed()).thenThrow(new RuntimeException("db down"));

        new NotificationLedgerReaper(ledgerMapper, meterRegistry).reap();

        verify(meterRegistry, never()).counter(anyString());
    }
}

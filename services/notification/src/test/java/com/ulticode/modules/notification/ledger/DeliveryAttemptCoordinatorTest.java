package com.ulticode.modules.notification.ledger;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.ledger.entity.NotificationDeliveryLedger;
import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Delivery attempt coordinator")
class DeliveryAttemptCoordinatorTest {

    @Mock
    private NotificationDeliveryLedgerMapper ledgerMapper;

    private final NotificationIntent intent = new AchievementEarnedIntent(
            "user-1", "achievement-1", "badge-test", "Test", "desc", null, 1, 10,
            java.time.Instant.parse("2026-01-01T00:00:00Z"), NotificationCategory.SYSTEM);

    @Test
    @DisplayName("positive claim count becomes ACQUIRED without a row lookup")
    void positiveClaimIsAcquired() {
        when(ledgerMapper.tryClaim(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);

        DeliveryAttemptCoordinator coordinator = new DeliveryAttemptCoordinator(ledgerMapper);

        assertThat(coordinator.claim(intent, "in_app", "owner-1").outcome())
                .isEqualTo(DeliveryAttemptCoordinator.ClaimOutcome.ACQUIRED);
        verify(ledgerMapper, never()).findByIntentAndChannel(anyString(), anyString());
    }

    @Test
    @DisplayName("a claimed row becomes IN_FLIGHT")
    void claimedRowIsInFlight() {
        when(ledgerMapper.tryClaim(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(ledgerMapper.findByIntentAndChannel(anyString(), anyString()))
                .thenReturn(ledger(DeliveryState.CLAIMED, 0));

        DeliveryAttemptCoordinator coordinator = new DeliveryAttemptCoordinator(ledgerMapper);

        assertThat(coordinator.claim(intent, "in_app", "owner-1"))
                .satisfies(result -> {
                    assertThat(result.outcome())
                            .isEqualTo(DeliveryAttemptCoordinator.ClaimOutcome.IN_FLIGHT);
                    assertThat(result.state()).isEqualTo(DeliveryState.CLAIMED);
                });
    }

    @Test
    @DisplayName("a delivered row becomes TERMINAL")
    void deliveredRowIsTerminal() {
        when(ledgerMapper.tryClaim(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(ledgerMapper.findByIntentAndChannel(anyString(), anyString()))
                .thenReturn(ledger(DeliveryState.DELIVERED, 0));

        DeliveryAttemptCoordinator coordinator = new DeliveryAttemptCoordinator(ledgerMapper);

        assertThat(coordinator.claim(intent, "in_app", "owner-1"))
                .satisfies(result -> {
                    assertThat(result.outcome())
                            .isEqualTo(DeliveryAttemptCoordinator.ClaimOutcome.TERMINAL);
                    assertThat(result.isTerminalSuccess()).isTrue();
                });
    }
    @Test
    @DisplayName("an exhausted failed row becomes TERMINAL")
    void exhaustedFailureIsTerminal() {
        when(ledgerMapper.tryClaim(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(ledgerMapper.findByIntentAndChannel(anyString(), anyString()))
                .thenReturn(ledger(DeliveryState.FAILED, 5));

        DeliveryAttemptCoordinator coordinator = new DeliveryAttemptCoordinator(ledgerMapper);

        assertThat(coordinator.claim(intent, "in_app", "owner-1"))
                .satisfies(result -> {
                    assertThat(result.outcome())
                            .isEqualTo(DeliveryAttemptCoordinator.ClaimOutcome.TERMINAL);
                    assertThat(result.isTerminalSuccess()).isFalse();
                    assertThat(result.state()).isEqualTo(DeliveryState.FAILED);
                });
    }

    @Test
    @DisplayName("a retryable failed row becomes BACKOFF")
    void retryableFailureIsBackoff() {
        when(ledgerMapper.tryClaim(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);
        when(ledgerMapper.findByIntentAndChannel(anyString(), anyString()))
                .thenReturn(ledger(DeliveryState.FAILED, 2));

        DeliveryAttemptCoordinator coordinator = new DeliveryAttemptCoordinator(ledgerMapper);

        assertThat(coordinator.claim(intent, "in_app", "owner-1"))
                .satisfies(result -> {
                    assertThat(result.outcome())
                            .isEqualTo(DeliveryAttemptCoordinator.ClaimOutcome.BACKOFF);
                    assertThat(result.state()).isEqualTo(DeliveryState.FAILED);
                });
    }

    @Test
    @DisplayName("confirmation distinguishes confirmed, already confirmed, and lost lease")
    void confirmationsAreTyped() {
        when(ledgerMapper.markDelivered("intent", "in_app", "owner-1"))
                .thenReturn(1, 0, 0);
        when(ledgerMapper.findByIntentAndChannel("intent", "in_app"))
                .thenReturn(ledger(DeliveryState.DELIVERED, 0),
                        ledger(DeliveryState.CLAIMED, 0));

        DeliveryAttemptCoordinator coordinator = new DeliveryAttemptCoordinator(ledgerMapper);

        assertThat(coordinator.confirmDelivered("intent", "in_app", "owner-1"))
                .isEqualTo(DeliveryAttemptCoordinator.Confirmation.CONFIRMED);
        assertThat(coordinator.confirmDelivered("intent", "in_app", "owner-1"))
                .isEqualTo(DeliveryAttemptCoordinator.Confirmation.ALREADY_CONFIRMED);
        assertThat(coordinator.confirmDelivered("intent", "in_app", "owner-1"))
                .isEqualTo(DeliveryAttemptCoordinator.Confirmation.LOST_LEASE);
    }

    @Test
    @DisplayName("reaping returns a typed count for the scheduling adapter")
    void reapingReturnsTypedResult() {
        when(ledgerMapper.reapStaleClaimed()).thenReturn(2);
        DeliveryAttemptCoordinator coordinator = new DeliveryAttemptCoordinator(ledgerMapper);
        DeliveryAttemptCoordinator.ReapResult result = coordinator.reapStaleClaims();

        assertThat(result.reaped()).isEqualTo(2);
        assertThat(result.hasReapedRows()).isTrue();
    }

    private static NotificationDeliveryLedger ledger(DeliveryState state, int reclaimAttempts) {
        return NotificationDeliveryLedger.builder()
                .deliveryState(state)
                .reclaimAttempts(reclaimAttempts)
                .build();
    }
}

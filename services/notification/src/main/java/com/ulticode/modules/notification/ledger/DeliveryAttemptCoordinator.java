package com.ulticode.modules.notification.ledger;

import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.ledger.entity.NotificationDeliveryLedger;
import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Owns interpretation of the notification ledger's affected-row protocol.
 *
 * <p>The mapper intentionally remains a SQL adapter and returns affected-row
 * counts. This module turns those counts, and the one required row lookup when
 * a claim is unavailable, into delivery-attempt outcomes used by dispatch and
 * reaping. The retry state and numbers remain owned by the ledger SQL.
 */
@Component
public final class DeliveryAttemptCoordinator {

    private static final int MAX_RECLAIM_ATTEMPTS = 5;

    private final NotificationDeliveryLedgerMapper ledgerMapper;

    public DeliveryAttemptCoordinator(NotificationDeliveryLedgerMapper ledgerMapper) {
        this.ledgerMapper = Objects.requireNonNull(ledgerMapper, "ledgerMapper");
    }

    /**
     * Attempts to acquire the delivery lease for one intent/channel pair.
     *
     * <p>A zero affected-row result is classified from the existing row in one
     * place so callers never need to infer whether the lease is in flight, the
     * row is terminal, or retry backoff is still active.
     */
    public ClaimResult claim(NotificationIntent intent, String channelId, String claimOwner) {
        int claimed = ledgerMapper.tryClaim(
                intent.intentId(), channelId, intent.userId(), intent.wireType(), claimOwner);
        if (claimed > 0) {
            return new ClaimResult(ClaimOutcome.ACQUIRED, DeliveryState.CLAIMED);
        }
        return classifyUnavailableClaim(
                ledgerMapper.findByIntentAndChannel(intent.intentId(), channelId));
    }

    /**
     * Confirms a successful channel delivery while preserving the mapper's
     * owner fence. A terminal row written by another owner is an idempotent
     * confirmation, not a lost delivery.
     */
    public Confirmation confirmDelivered(String intentId, String channelId, String claimOwner) {
        int updated = ledgerMapper.markDelivered(intentId, channelId, claimOwner);
        return updated > 0
                ? Confirmation.CONFIRMED
                : confirmExistingState(intentId, channelId, DeliveryState.DELIVERED);
    }

    /**
     * Confirms that a channel which does not support an intent was skipped.
     */
    public Confirmation confirmSkipped(String intentId, String channelId, String claimOwner) {
        int updated = ledgerMapper.markSkipped(intentId, channelId, claimOwner);
        return updated > 0
                ? Confirmation.CONFIRMED
                : confirmExistingState(intentId, channelId, DeliveryState.SKIPPED);
    }

    /**
     * Records a failed delivery attempt. Failure recording remains best-effort
     * at the synchronous dispatcher boundary, but its owner-fenced result is
     * still typed for callers that need to observe it.
     */
    public Confirmation recordFailure(String intentId,
                                      String channelId,
                                      String failureReason,
                                      String claimOwner) {
        int updated = ledgerMapper.markFailed(intentId, channelId, failureReason, claimOwner);
        return updated > 0 ? Confirmation.CONFIRMED : Confirmation.LOST_LEASE;
    }

    /**
     * Runs the stale-lease SQL and returns a typed reaper result.
     */
    public ReapResult reapStaleClaims() {
        return new ReapResult(ledgerMapper.reapStaleClaimed());
    }

    private ClaimResult classifyUnavailableClaim(NotificationDeliveryLedger ledger) {
        if (ledger == null) {
            return new ClaimResult(ClaimOutcome.BACKOFF, null);
        }

        DeliveryState state = ledger.getDeliveryState();
        if (state == DeliveryState.CLAIMED) {
            return new ClaimResult(ClaimOutcome.IN_FLIGHT, state);
        }
        if (state == DeliveryState.DELIVERED || state == DeliveryState.SKIPPED) {
            return new ClaimResult(ClaimOutcome.TERMINAL, state);
        }
        if (state == DeliveryState.FAILED
                && ledger.getReclaimAttempts() != null
                && ledger.getReclaimAttempts() >= MAX_RECLAIM_ATTEMPTS) {
            return new ClaimResult(ClaimOutcome.TERMINAL, state);
        }

        // The mapper owns the five-minute predicate. Any non-terminal FAILED
        // row which was not claimed remains unavailable to this attempt.
        return new ClaimResult(ClaimOutcome.BACKOFF, state);
    }

    private Confirmation confirmExistingState(String intentId,
                                               String channelId,
                                               DeliveryState expectedState) {
        NotificationDeliveryLedger existing = ledgerMapper.findByIntentAndChannel(intentId, channelId);
        return existing != null && existing.getDeliveryState() == expectedState
                ? Confirmation.ALREADY_CONFIRMED
                : Confirmation.LOST_LEASE;
    }

    public enum ClaimOutcome {
        ACQUIRED,
        IN_FLIGHT,
        TERMINAL,
        BACKOFF
    }

    public record ClaimResult(ClaimOutcome outcome, DeliveryState state) {
        public ClaimResult {
            Objects.requireNonNull(outcome, "outcome");
        }

        public boolean isAcquired() {
            return outcome == ClaimOutcome.ACQUIRED;
        }

        public boolean isTerminalSuccess() {
            return outcome == ClaimOutcome.TERMINAL
                    && (state == DeliveryState.DELIVERED || state == DeliveryState.SKIPPED);
        }

        public String stateName() {
            return state == null ? "missing" : state.name();
        }
    }

    public enum Confirmation {
        CONFIRMED,
        ALREADY_CONFIRMED,
        LOST_LEASE;

        public boolean isSuccessful() {
            return this != LOST_LEASE;
        }
    }

    public record ReapResult(int reaped) {
        public boolean hasReapedRows() {
            return reaped > 0;
        }
    }
}

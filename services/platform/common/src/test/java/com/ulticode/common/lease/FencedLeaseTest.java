package com.ulticode.common.lease;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FencedLease")
class FencedLeaseTest {

    private static final Instant START = Instant.parse("2026-08-31T00:00:00Z");
    private static final Duration TTL = Duration.ofSeconds(10);

    @Test
    @DisplayName("duplicate runner is rejected until expiry, then receives a higher fence")
    void duplicateRunnerAndExpiry() {
        FencedLease first = FencedLease.tryAcquire(
                null, "job", "runner-a", 1, START, TTL).orElseThrow();

        assertThat(FencedLease.tryAcquire(
                first, "job", "runner-b", 2, START.plusSeconds(1), TTL)).isEmpty();

        FencedLease second = FencedLease.tryAcquire(
                first, "job", "runner-b", 2, START.plusSeconds(10), TTL).orElseThrow();
        assertThat(second.fenceToken()).isEqualTo(2);
        assertThat(second.ownerToken()).isEqualTo("runner-b");
    }

    @Test
    @DisplayName("lost lease rejects stale completion and renewal")
    void staleCompletionIsRejected() {
        FencedLease first = FencedLease.tryAcquire(
                null, "job", "runner-a", 1, START, TTL).orElseThrow();
        FencedLease second = FencedLease.tryAcquire(
                first, "job", "runner-b", 2, START.plusSeconds(10), TTL).orElseThrow();

        assertThat(second.permits(first.ownerToken(), first.fenceToken(),
                START.plusSeconds(11), Duration.ZERO)).isFalse();
        assertThat(first.renew(first.ownerToken(), first.fenceToken(),
                START.plusSeconds(11), TTL, Duration.ZERO)).isEmpty();
    }

    @Test
    @DisplayName("clock skew and pause fail closed before the expiry boundary")
    void clockSkewAndPauseFailClosed() {
        FencedLease lease = FencedLease.tryAcquire(
                null, "job", "runner-a", 1, START, TTL).orElseThrow();

        assertThat(lease.permits("runner-a", 1, START.plusSeconds(2),
                Duration.ofSeconds(1))).isTrue();
        assertThat(lease.permits("runner-a", 1, START.minusSeconds(2),
                Duration.ofSeconds(1))).isFalse();
        assertThat(lease.permits("runner-a", 1, START.plusSeconds(9),
                Duration.ofSeconds(2))).isFalse();
        assertThat(lease.permits("runner-a", 1, START.plusSeconds(10),
                Duration.ZERO)).isFalse();
    }
}

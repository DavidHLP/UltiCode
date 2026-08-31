package com.ulticode.common.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DependencyGuard")
class DependencyGuardTest {

    @Test
    @DisplayName("consecutive failures open, one half-open probe recovers, and saturation fails fast")
    void opensProbesRecoversAndBoundsConcurrency() {
        AtomicLong now = new AtomicLong(1_000L);
        DependencyGuard guard = new DependencyGuard(
                1, 2, Duration.ofSeconds(10), now::get);

        fail(guard);
        fail(guard);

        assertThat(guard.state()).isEqualTo(DependencyGuard.State.OPEN);
        assertRejected(guard, DependencyGuard.Rejection.CIRCUIT_OPEN);

        now.addAndGet(Duration.ofSeconds(10).toMillis());
        DependencyGuard.Permit probe = guard.acquire();
        assertThat(guard.state()).isEqualTo(DependencyGuard.State.HALF_OPEN);
        assertRejected(guard, DependencyGuard.Rejection.CIRCUIT_OPEN);

        probe.success();

        assertThat(guard.state()).isEqualTo(DependencyGuard.State.CLOSED);
        DependencyGuard.Permit active = guard.acquire();
        assertThat(guard.inFlight()).isOne();
        assertRejected(guard, DependencyGuard.Rejection.SATURATED);
        active.ignore();
        assertThat(guard.inFlight()).isZero();
    }

    @Test
    @DisplayName("a failed half-open probe reopens for the full delay")
    void failedProbeReopens() {
        AtomicLong now = new AtomicLong();
        DependencyGuard guard = new DependencyGuard(
                2, 1, Duration.ofSeconds(5), now::get);

        fail(guard);
        now.addAndGet(Duration.ofSeconds(5).toMillis());
        fail(guard);

        assertThat(guard.state()).isEqualTo(DependencyGuard.State.OPEN);
        now.addAndGet(Duration.ofSeconds(4).toMillis());
        assertRejected(guard, DependencyGuard.Rejection.CIRCUIT_OPEN);
    }

    private static void fail(DependencyGuard guard) {
        DependencyGuard.Permit permit = guard.acquire();
        permit.failure();
    }

    private static void assertRejected(
            DependencyGuard guard, DependencyGuard.Rejection expected) {
        assertThatThrownBy(guard::acquire)
                .isInstanceOf(DependencyGuard.RejectedException.class)
                .extracting(exception -> ((DependencyGuard.RejectedException) exception).reason())
                .isEqualTo(expected);
    }
}

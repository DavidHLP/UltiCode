package com.ulticode.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link FakeTimeSource}, the test adapter for
 * {@link TimeSource}.
 *
 * <p>These tests pin the deterministic behaviour the production
 * callers rely on: the fake does not tick by itself, only when
 * {@code advance} / {@code advanceNanos} / {@code pinXxx} is called.
 */
@DisplayName("FakeTimeSource")
class FakeTimeSourceTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("default constructor pins both clocks to 0")
        void defaultConstructorPinsBothClocksToZero() {
            FakeTimeSource ts = new FakeTimeSource();
            assertThat(ts.wallMillis()).isEqualTo(0L);
            assertThat(ts.monotonicNanos()).isEqualTo(0L);
        }

        @Test
        @DisplayName("explicit constructor pins both clocks to the given values")
        void explicitConstructorPinsBothClocksToGivenValues() {
            FakeTimeSource ts = new FakeTimeSource(1234L, 5678L);
            assertThat(ts.wallMillis()).isEqualTo(1234L);
            assertThat(ts.monotonicNanos()).isEqualTo(5678L);
        }
    }

    @Nested
    @DisplayName("wall clock")
    class WallClock {

        @Test
        @DisplayName("advance(millis) moves the wall clock forward by the given delta")
        void advanceMovesTheWallClockForward() {
            FakeTimeSource ts = new FakeTimeSource(1000L, 0L);
            long after = ts.advance(500L);
            assertThat(after).isEqualTo(1500L);
            assertThat(ts.wallMillis()).isEqualTo(1500L);
        }

        @Test
        @DisplayName("advance() does not affect the monotonic clock")
        void advanceDoesNotAffectMonotonicClock() {
            FakeTimeSource ts = new FakeTimeSource(0L, 42L);
            ts.advance(1000L);
            assertThat(ts.monotonicNanos()).isEqualTo(42L);
        }

        @Test
        @DisplayName("pinWall(millis) sets the wall clock to a specific value")
        void pinWallSetsTheWallClock() {
            FakeTimeSource ts = new FakeTimeSource(1000L, 0L);
            ts.pinWall(9999L);
            assertThat(ts.wallMillis()).isEqualTo(9999L);
        }
    }

    @Nested
    @DisplayName("monotonic clock")
    class MonotonicClock {

        @Test
        @DisplayName("advanceNanos(nanos) moves the monotonic clock forward by the given delta")
        void advanceNanosMovesTheMonotonicClockForward() {
            FakeTimeSource ts = new FakeTimeSource(0L, 1000L);
            long after = ts.advanceNanos(500L);
            assertThat(after).isEqualTo(1500L);
            assertThat(ts.monotonicNanos()).isEqualTo(1500L);
        }

        @Test
        @DisplayName("advanceNanos() does not affect the wall clock")
        void advanceNanosDoesNotAffectWallClock() {
            FakeTimeSource ts = new FakeTimeSource(42L, 0L);
            ts.advanceNanos(1_000_000L);
            assertThat(ts.wallMillis()).isEqualTo(42L);
        }

        @Test
        @DisplayName("pinMonotonic(nanos) sets the monotonic clock to a specific value")
        void pinMonotonicSetsTheMonotonicClock() {
            FakeTimeSource ts = new FakeTimeSource(0L, 1000L);
            ts.pinMonotonic(9999L);
            assertThat(ts.monotonicNanos()).isEqualTo(9999L);
        }
    }
}

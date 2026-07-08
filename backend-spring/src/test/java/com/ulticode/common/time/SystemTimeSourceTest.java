package com.ulticode.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SystemTimeSource}, the production adapter for
 * {@link TimeSource}.
 *
 * <p>Tests the contract only &mdash; the JVM is the boundary. The
 * production adapter must be monotonically non-decreasing for
 * {@code monotonicNanos} (with the standard JVM epsilon) and must
 * return a positive, non-NaN wall-clock value.
 */
@DisplayName("SystemTimeSource")
class SystemTimeSourceTest {

    private final SystemTimeSource source = new SystemTimeSource();

    @Nested
    @DisplayName("wallMillis()")
    class WallMillis {

        @Test
        @DisplayName("returns a positive millis-since-epoch value")
        void returnsPositiveValue() {
            long now = source.wallMillis();
            assertThat(now).isGreaterThan(0L);
        }

        @Test
        @DisplayName("returns a non-decreasing value across two reads")
        void returnsNonDecreasingValue() {
            long first = source.wallMillis();
            long second = source.wallMillis();
            assertThat(second).isGreaterThanOrEqualTo(first);
        }
    }

    @Nested
    @DisplayName("monotonicNanos()")
    class MonotonicNanos {

        @Test
        @DisplayName("returns a value that is comparable (delta is non-negative)")
        void returnsComparableValue() {
            long a = source.monotonicNanos();
            long b = source.monotonicNanos();
            assertThat(b).isGreaterThanOrEqualTo(a);
        }
    }
}

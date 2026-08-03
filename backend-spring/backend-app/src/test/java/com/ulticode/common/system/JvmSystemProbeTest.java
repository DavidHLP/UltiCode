package com.ulticode.common.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JvmSystemProbe}, the production adapter for
 * {@link SystemProbe}.
 *
 * <p>Tests the contract — the three signals return non-null numerics and
 * the {@code -1.0} fallback contract is reachable only when the host JVM
 * genuinely lacks the {@code com.sun.management} extension. We do not mock
 * {@code ManagementFactory}: the prod adapter is the boundary.
 */
@DisplayName("JvmSystemProbe")
class JvmSystemProbeTest {

    private final JvmSystemProbe probe = new JvmSystemProbe();

    @Nested
    @DisplayName("availableProcessors()")
    class AvailableProcessors {

        @Test
        @DisplayName("returns a positive integer in any real JVM")
        void returnsPositiveInteger() {
            int cores = probe.availableProcessors();
            assertThat(cores).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("processCpuLoad()")
    class ProcessCpuLoad {

        @Test
        @DisplayName("returns a value in [-1.0, 1.0]; never NaN or out of range")
        void returnsBoundedValue() {
            double load = probe.processCpuLoad();
            assertThat(Double.isNaN(load)).isFalse();
            assertThat(load).isBetween(-1.0, 1.0);
        }
    }

    @Nested
    @DisplayName("systemCpuLoad()")
    class SystemCpuLoad {

        @Test
        @DisplayName("returns a value in [-1.0, 1.0]; never NaN or out of range")
        void returnsBoundedValue() {
            double load = probe.systemCpuLoad();
            assertThat(Double.isNaN(load)).isFalse();
            assertThat(load).isBetween(-1.0, 1.0);
        }
    }
}

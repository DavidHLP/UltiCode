package com.ulticode.common.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link TimeSourceHolder}. The holder is the bridge
 * that static utility call sites (e.g. {@code TraceIdUtil.current()})
 * use to reach the active {@link TimeSource} without being rewritten
 * as instance methods.
 */
@DisplayName("TimeSourceHolder")
class TimeSourceHolderTest {

    @AfterEach
    void tearDown() {
        TimeSourceHolder.reset();
    }

    @Nested
    @DisplayName("install() / get()")
    class InstallAndGet {

        @Test
        @DisplayName("get() returns the fallback when no source was installed")
        void getReturnsFallbackWhenNoSourceInstalled() {
            TimeSourceHolder.reset();
            TimeSource source = TimeSourceHolder.get();
            assertThat(source).isNotNull();
            // Fallback reaches the JVM, so we only assert non-null behaviour.
            assertThat(source.wallMillis()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("get() returns the installed source after install()")
        void getReturnsInstalledSource() {
            FakeTimeSource fake = new FakeTimeSource(100L, 200L);
            TimeSourceHolder.install(fake);
            assertThat(TimeSourceHolder.get()).isSameAs(fake);
            assertThat(TimeSourceHolder.get().wallMillis()).isEqualTo(100L);
            assertThat(TimeSourceHolder.get().monotonicNanos()).isEqualTo(200L);
        }

        @Test
        @DisplayName("install(null) throws IllegalArgumentException")
        void installNullThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TimeSourceHolder.install(null));
        }
    }

    @Nested
    @DisplayName("reset()")
    class Reset {

        @Test
        @DisplayName("reset() returns to the fallback")
        void resetReturnsToFallback() {
            TimeSourceHolder.install(new FakeTimeSource());
            TimeSourceHolder.reset();
            assertThat(TimeSourceHolder.get().wallMillis()).isGreaterThan(0L);
        }
    }
}

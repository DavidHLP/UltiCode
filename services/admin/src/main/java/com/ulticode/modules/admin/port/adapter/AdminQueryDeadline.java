package com.ulticode.modules.admin.port.adapter;

import com.ulticode.common.time.TimeSource;
import com.ulticode.common.time.TimeSourceHolder;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Monotonic wall-budget policy for one Admin owner-query use case.
 *
 * <p>The deadline is created once at the start of a fan-out and every serial
 * round consumes the same remaining budget. This keeps timeout arithmetic out
 * of individual adapters while preserving their separate executor pools.</p>
 */
public final class AdminQueryDeadline {

    private final TimeSource timeSource;

    public AdminQueryDeadline(TimeSource timeSource) {
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    }

    /** Create a deadline relative to the injected monotonic source. */
    public Deadline start(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        long now = timeSource.monotonicNanos();
        if (timeout <= 0) {
            return new Deadline(now, timeSource);
        }
        long duration = unit.toNanos(timeout);
        try {
            return new Deadline(Math.addExact(now, duration), timeSource);
        } catch (ArithmeticException overflow) {
            return new Deadline(Long.MAX_VALUE, timeSource);
        }
    }

    /** Test-only convenience that uses the platform fallback source. */
    public static AdminQueryDeadline system() {
        return new AdminQueryDeadline(TimeSourceHolder.get());
    }

    public record Deadline(long deadlineNanos, TimeSource timeSource) {

        public Deadline {
            Objects.requireNonNull(timeSource, "timeSource");
        }

        /** Return the non-negative monotonic time left in this budget. */
        public long remainingNanos() {
            long now = timeSource.monotonicNanos();
            return deadlineNanos >= now ? deadlineNanos - now : 0L;
        }
    }
}

package com.ulticode.common.time;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static accessor that static utility paths (e.g. {@code TraceIdUtil})
 * use to reach the active {@link TimeSource} without being rewritten
 * as instance methods. The production source is installed by
 * {@code TimeConfig#installSystemSource(TimeSource)} on startup; tests
 * that exercise a static utility call site should either
 * (a) install a {@link FakeTimeSource} via the same setter, or
 * (b) keep their assertions prefix-only (the existing pattern in
 * {@code GlobalExceptionHandlerTest}: {@code assertTrue(id.startsWith("t-"))}).
 *
 * <p><b>Why a holder at all</b>: the alternative &mdash; converting
 * {@code TraceIdUtil} to a {@code @Component} and rewriting all 24
 * call sites &mdash; trades a tiny testability gain for a 24-site
 * churn and a worse developer experience. Static utilities that
 * stamp a debug string and are never asserted on for their millis
 * value are below the bar for full DI conversion; the
 * {@link TimeSource} seam still concentrates the time call in one
 * place and lets a test install a fake for the rare case where a
 * pinned wall value matters.
 */
public final class TimeSourceHolder {

    private static final AtomicReference<TimeSource> CURRENT = new AtomicReference<>();

    private TimeSourceHolder() {
    }

    /**
     * Install the active {@link TimeSource}. Production wiring calls
     * this once at startup with a {@link SystemTimeSource}; tests
     * install a {@link FakeTimeSource} before exercising the static
     * call site.
     */
    public static void install(TimeSource source) {
        if (source == null) {
            throw new IllegalArgumentException("TimeSource must not be null");
        }
        CURRENT.set(source);
    }

    /**
     * @return the active source, or a fallback that returns
     *         {@code System.currentTimeMillis()} / {@code System.nanoTime()}
     *         if no source was installed (defensive: lets the app start
     *         even if a test config forgot to install).
     */
    public static TimeSource get() {
        TimeSource installed = CURRENT.get();
        if (installed != null) {
            return installed;
        }
        return Fallback.INSTANCE;
    }

    /** Reset to the fallback (used by test teardown when needed). */
    public static void reset() {
        CURRENT.set(null);
    }

    private enum Fallback implements TimeSource {
        INSTANCE;

        @Override
        public long wallMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long monotonicNanos() {
            return System.nanoTime();
        }
    }
}

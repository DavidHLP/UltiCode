package com.ulticode.common.time;

import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link TimeSource}. Delegates to the JVM's
 * static time factories.
 *
 * <p>This is the only {@code @Component} implementation; the test
 * adapter ({@link FakeTimeSource}) is constructed directly by unit
 * tests. Wiring is auto-discovered by Spring &mdash; no explicit
 * {@code @Bean} method is required, but {@code TimeConfig} exists for
 * parity with {@code ClockConfig} and to install the production source
 * into the {@link TimeSourceHolder} that the static
 * {@code TraceIdUtil.current()} path consults.
 */
@Component
public class SystemTimeSource implements TimeSource {

    @Override
    public long wallMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long monotonicNanos() {
        return System.nanoTime();
    }
}

package com.ulticode.common.time;

import org.springframework.context.annotation.Configuration;

/**
 * Holder for {@link TimeSource} wiring. The production
 * {@link SystemTimeSource} is auto-discovered as a {@code @Component};
 * this class exists for parity with {@code ClockConfig} and to install
 * the active source into {@link TimeSourceHolder} so that static
 * utility call sites (e.g. {@code TraceIdUtil.current()}) reach the
 * same instance that the rest of the backend uses.
 */
@Configuration
public class TimeConfig {

    public TimeConfig(SystemTimeSource systemTimeSource) {
        TimeSourceHolder.install(systemTimeSource);
    }
}

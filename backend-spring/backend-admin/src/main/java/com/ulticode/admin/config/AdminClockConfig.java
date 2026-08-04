package com.ulticode.admin.config;

import com.ulticode.common.time.TimeSource;
import com.ulticode.common.time.TimeSourceHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Admin-owned JVM {@link Clock} bean (P7-LEGACY-ADMIN-CONFIG-OWN-001).
 *
 * <p>Replaces the bean the admin shell currently discovers from Legacy
 * {@code com.ulticode.common.config.ClockConfig} via the broad
 * {@code com.ulticode} scan. Admin main code injects {@code Clock} in 23
 * files; the Legacy config class is deleted by DEAD-INFRA-DELETE.
 *
 * <p>Production behavior unchanged: returns {@link Clock#systemDefaultZone()}.
 * Mirrors {@code AppClockConfig} / {@code AuthClockConfig}.
 *
 * <p>P7-RELOCATE: also owns the {@link TimeSource} bean — the App-side
 * {@code AppClockConfig} that used to supply it sits in the excluded
 * {@code com.ulticode.app.config} package, while
 * {@code com.ulticode.common.metrics.SqlTimingInterceptor} (required by the
 * MyBatis-Plus auto-configuration) injects it here.
 */
@Configuration
public class AdminClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public TimeSource systemTimeSource() {
        TimeSource source = new TimeSource() {
            @Override
            public long wallMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public long monotonicNanos() {
                return System.nanoTime();
            }
        };
        TimeSourceHolder.install(source);
        return source;
    }
}

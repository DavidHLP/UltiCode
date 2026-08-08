package com.ulticode.auth.config;

import com.ulticode.common.time.TimeSource;
import com.ulticode.common.time.TimeSourceHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the {@link Clock} and {@link TimeSource} beans consumed by backend-auth.
 */
@Configuration
public class AuthClockConfig {

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

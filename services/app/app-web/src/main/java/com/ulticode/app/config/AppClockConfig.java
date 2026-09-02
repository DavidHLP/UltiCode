package com.ulticode.app.config;

import com.ulticode.common.time.TimeSource;
import com.ulticode.common.time.TimeSourceHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Clock and {@link TimeSource} configuration for backend-app.
 *
 * <p>Provides the {@code TimeSource} seam required by {@code SqlTimingInterceptor}
 * and other app components. The judge-runtime keeps a copy of this configuration
 * for the standalone judge worker; backend-app must own its copy because the
 * production dev classpath does not include {@code backend-judge-runtime}.
 */
@Configuration
public class AppClockConfig {
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

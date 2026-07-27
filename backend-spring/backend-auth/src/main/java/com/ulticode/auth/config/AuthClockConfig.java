package com.ulticode.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link Clock} bean consumed by time-sensitive auth
 * services (e.g. {@code RefreshTokenService}).
 *
 * <p>Private copy inside backend-auth of backend-legacy's
 * {@code com.ulticode.common.config.ClockConfig}. backend-auth does not
 * depend on backend-legacy, so the {@code Clock} bean must be installed
 * locally. The Strangler Fig contract keeps the legacy bean unchanged
 * until Phase 4 cutover; the two beans are equivalent (both delegate to
 * {@link Clock#systemDefaultZone()} in production and accept
 * {@code @MockBean} / {@code @Primary} replacement in tests).
 */
@Configuration
public class AuthClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}

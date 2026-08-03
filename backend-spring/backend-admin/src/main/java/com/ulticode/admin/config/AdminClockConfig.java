package com.ulticode.admin.config;

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
 */
@Configuration
public class AdminClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}

package com.ulticode.notification.config;

import com.ulticode.common.uuid.UuidGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

/** Minimal infrastructure owned by the notification service shell. */
@Configuration
public class NotificationRuntimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public UuidGenerator uuidGenerator() {
        return () -> UUID.randomUUID().toString();
    }
}

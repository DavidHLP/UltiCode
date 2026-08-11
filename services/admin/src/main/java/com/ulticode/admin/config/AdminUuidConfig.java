package com.ulticode.admin.config;

import com.ulticode.common.uuid.UuidGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Admin-owned production UUID generator.
 */
@Configuration
public class AdminUuidConfig {

    @Bean
    public UuidGenerator adminUuidGenerator() {
        return () -> UUID.randomUUID().toString();
    }
}

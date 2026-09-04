package com.ulticode.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Keeps the liveness/readiness probe usable without an application session. */
@Configuration(proxyBeanMethods = false)
final class CoreSecurityConfiguration {

    @Bean
    SecurityFilterChain coreReadinessSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/core/health/ready").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}

package com.ulticode.observability.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.net.URI;

/** Fails closed before an OTLP authorization header can leave over HTTP. */
@AutoConfiguration
public class OtlpSecurityAutoConfiguration {

    @Bean
    OtlpEndpointSecurityPolicy otlpEndpointSecurityPolicy(Environment environment) {
        String endpoint = environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint");
        String authorization = environment.getProperty(
                "management.opentelemetry.tracing.export.otlp.headers.Authorization");
        return OtlpEndpointSecurityPolicy.validate(endpoint, authorization);
    }

    static final class OtlpEndpointSecurityPolicy {

        static OtlpEndpointSecurityPolicy validate(String endpoint, String authorization) {
            if (authorization == null || authorization.isBlank()) {
                return new OtlpEndpointSecurityPolicy();
            }
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException(
                        "management.opentelemetry.tracing.export.otlp.endpoint is required when authorization is configured");
            }
            try {
                URI uri = URI.create(endpoint.trim());
                if (!"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new IllegalStateException(
                            "management.opentelemetry.tracing.export.otlp.endpoint must use https when authorization is configured");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "management.opentelemetry.tracing.export.otlp.endpoint is not a valid URI", exception);
            }
            return new OtlpEndpointSecurityPolicy();
        }
    }
}

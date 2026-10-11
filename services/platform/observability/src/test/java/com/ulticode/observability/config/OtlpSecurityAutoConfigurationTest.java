package com.ulticode.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtlpSecurityAutoConfigurationTest {

    @Test
    void localHttpEndpointIsAllowedWithoutCredentials() {
        assertThatCode(() -> OtlpSecurityAutoConfiguration.OtlpEndpointSecurityPolicy
                .validate("http://localhost:4318/v1/traces", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void httpEndpointIsRejectedWhenAuthorizationIsConfigured() {
        assertThatThrownBy(() -> OtlpSecurityAutoConfiguration.OtlpEndpointSecurityPolicy
                .validate("http://collector.example/v1/traces", "Bearer secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use https");
    }

    @Test
    void httpsEndpointIsAllowedWhenAuthorizationIsConfigured() {
        assertThatCode(() -> OtlpSecurityAutoConfiguration.OtlpEndpointSecurityPolicy
                .validate("https://collector.example/v1/traces", "Bearer secret"))
                .doesNotThrowAnyException();
    }

    @Test
    void autoConfigurationReadsResolvedProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.opentelemetry.tracing.export.otlp.endpoint",
                        "http://collector.example/v1/traces")
                .withProperty("management.opentelemetry.tracing.export.otlp.headers.Authorization",
                        "Bearer secret");

        assertThatThrownBy(() -> new OtlpSecurityAutoConfiguration()
                .otlpEndpointSecurityPolicy(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use https");
    }

    @Test
    void boot4TracingPropertiesBindFromTheManagedOtlpPrefix() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.opentelemetry.tracing.export.otlp.endpoint",
                        "https://collector.example/v1/traces")
                .withProperty("management.opentelemetry.tracing.export.otlp.headers.Authorization",
                        "Bearer secret");

        OtlpTracingProperties properties = Binder.get(environment)
                .bind("management.opentelemetry.tracing.export.otlp", OtlpTracingProperties.class)
                .get();

        assertThat(properties.getEndpoint()).isEqualTo("https://collector.example/v1/traces");
        assertThat(properties.getHeaders()).containsEntry("Authorization", "Bearer secret");
    }
}

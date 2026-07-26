package com.ulticode.migration.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * P1-INFRA-003: Dubbo 3.3.6 + Triple + Nacos registry configuration binding test.
 *
 * <p>Only verifies that {@code application-dubbo-smoke-test.yml} properties are
 * bound correctly. No DubboAutoConfiguration is loaded, so no Nacos connection
 * is attempted and the test stays fast/stable. The live registration check is
 * performed by {@code scripts/dev/dubbo-nacos-smoke.sh}.
 */
@SpringBootTest(
        classes = {DubboBootstrapConfigTest.EmptyConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dubbo-smoke-test")
@DisplayName("P1-INFRA-003: Dubbo configuration binding")
class DubboBootstrapConfigTest {

    @Autowired
    private Environment env;

    @Test
    @DisplayName("dubbo.application.name is bound")
    void applicationName() {
        assertThat(env.getProperty("dubbo.application.name")).isEqualTo("ulticode-backend-legacy");
    }

    @Test
    @DisplayName("dubbo.protocol.name is Triple")
    void tripleProtocol() {
        assertThat(env.getProperty("dubbo.protocol.name")).isEqualTo("tri");
    }

    @Test
    @DisplayName("dubbo.registry.address points to Nacos dev namespace")
    void nacosRegistry() {
        String address = env.getProperty("dubbo.registry.address");
        assertThat(address).contains("nacos://").contains("namespace=dev");
    }

    @Configuration
    static class EmptyConfig {
    }
}

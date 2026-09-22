package com.ulticode.admin.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.util.TestPropertyValues;

import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring proof for the admin-owned runtime config beans
 * (P7-LEGACY-ADMIN-CONFIG-OWN-001).
 */
class AdminRuntimeConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AdminClockConfig.class, AdminMybatisPlusConfig.class);

    @Test
    @DisplayName("Clock, MybatisPlusInterceptor and MetaObjectHandler beans resolve")
    void adminRuntimeBeansResolve() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            assertThat(context).hasSingleBean(MetaObjectHandler.class);
            assertThat(context.getBean(Clock.class)).isEqualTo(Clock.systemDefaultZone());
        });
    }

    @Test
    @DisplayName("Admin YAML binds RustFS CA and keeps avatar multipart headroom")
    void adminYamlContainsStorageTrustAndMultipartLimits() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "admin-application",
                new FileSystemResource("src/main/resources/application.yml"));
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        TestPropertyValues.of("APP_STORAGE_S3_CA_CERTIFICATE=/certs/rustfs_cert.pem")
                .applyTo(environment);

        sources.forEach(propertySources::addLast);

        assertThat(environment.getProperty("app.storage.s3.ca-certificate-path"))
                .isEqualTo("/certs/rustfs_cert.pem");
        assertThat(environment.getProperty("spring.servlet.multipart.max-file-size"))
                .isEqualTo("6MB");
        assertThat(environment.getProperty("spring.servlet.multipart.max-request-size"))
                .isEqualTo("6MB");
    }
}

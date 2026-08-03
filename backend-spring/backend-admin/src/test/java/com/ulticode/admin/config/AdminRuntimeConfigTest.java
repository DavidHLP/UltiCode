package com.ulticode.admin.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

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
}

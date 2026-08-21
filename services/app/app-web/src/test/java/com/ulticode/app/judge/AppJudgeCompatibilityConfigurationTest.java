package com.ulticode.app.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class AppJudgeCompatibilityConfigurationTest {

    @Test
    void devProfileDoesNotRegisterRollbackAdapterByDefault() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of(
                            "app.features.judge-compatibility-enabled", "false")));
            context.register(AppJudgeCompatibilityConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AppJudgeCompatibilityAdapter.class)).isEmpty();
        }
    }
}

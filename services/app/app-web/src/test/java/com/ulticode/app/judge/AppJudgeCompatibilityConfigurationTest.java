package com.ulticode.app.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class AppJudgeCompatibilityConfigurationTest {

    @Test
    void rollbackConditionRequiresCompatibilityFlagAndExplicitMode() {
        ConditionalOnExpression condition =
                AppJudgeCompatibilityConfiguration.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(condition).isNotNull();
        assertThat(condition.value())
                .contains("app.features.judge-compatibility-enabled")
                .contains("app.runtime.mode:dev-lite")
                .contains("legacy-rollback");
    }

    @Test
    void compatibilityConfigurationIsNotAutoDiscoveredByCurrentApp() {
        assertThat(AppJudgeCompatibilityConfiguration.class.getAnnotation(Configuration.class))
                .isNull();
    }

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

    @Test
    void compatibilityFlagCannotActivateRollbackAdapterInNormalDevMode() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of(
                            "app.features.judge-compatibility-enabled", "true",
                            "app.runtime.mode", "dev-lite")));
            context.register(AppJudgeCompatibilityConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AppJudgeCompatibilityAdapter.class)).isEmpty();
        }
    }
}

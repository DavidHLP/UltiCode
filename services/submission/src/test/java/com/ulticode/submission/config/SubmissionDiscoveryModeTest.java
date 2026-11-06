package com.ulticode.submission.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.spring.util.EnvironmentUtils;
import org.apache.dubbo.registry.client.migration.model.MigrationRule;
import org.apache.dubbo.registry.client.migration.model.MigrationStep;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionDiscoveryModeTest {

    @Test
    void packagedConfigurationSelectsApplicationDiscoveryThroughDubboSpringBridge() throws IOException {
        Map<String, String> properties = packagedDubboProperties();
        assertThat(properties).containsEntry("dubbo.application.register-mode", "instance");
        assertThat(resolveStep(properties)).isEqualTo(MigrationStep.FORCE_APPLICATION);
    }

    @Test
    void absentConfigurationRetainsDubboDualDiscoveryDefault() throws IOException {
        Map<String, String> properties = new HashMap<>(packagedDubboProperties());
        properties.remove("dubbo.application.migration.step");
        assertThat(resolveStep(properties)).isEqualTo(MigrationStep.APPLICATION_FIRST);
    }

    private static Map<String, String> packagedDubboProperties() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader().load("submission", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        // This is the same extraction performed by DubboContextPostProcessor.
        return EnvironmentUtils.filterDubboProperties(environment);
    }

    private static MigrationStep resolveStep(Map<String, String> properties) {
        FrameworkModel framework = new FrameworkModel();
        try {
            ApplicationModel application = framework.newApplication();
            application.getModelEnvironment().getAppConfigMap().putAll(properties);
            URL consumer = URL.valueOf("consumer://127.0.0.1/com.ulticode.app.api.service.ProblemFactsPort")
                    .setScopeModel(application.getDefaultModule());
            return MigrationRule.getInitRule().getStep(consumer);
        } finally {
            framework.destroy();
        }
    }
}

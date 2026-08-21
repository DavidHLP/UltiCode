package com.ulticode.app.judge;

import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.processor.DefaultJudgeAttemptExecutor;
import com.ulticode.modules.queue.processor.JudgeAttemptExecutor;
import com.ulticode.modules.queue.service.QueueService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Explicit, rollback-only wiring for the former App-local RQueue consumer.
 * Normal App/API boot does not own Judge polling; the independent Judge
 * process imports the full execution graph from {@code JudgeRuntimeConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(
        prefix = "app.features",
        name = "judge-compatibility-enabled",
        havingValue = "true")
@Import(DefaultJudgeAttemptExecutor.class)
public class AppJudgeCompatibilityConfiguration {

    @Bean
    AppJudgeCompatibilityAdapter appJudgeCompatibilityAdapter(
            QueueService queueService,
            QueueConfig queueConfig,
            JudgeFeatureFlagsPort featureFlags,
            JudgeAttemptExecutor attemptExecutor) {
        return new AppJudgeCompatibilityAdapter(queueService, queueConfig, featureFlags, attemptExecutor);
    }
}

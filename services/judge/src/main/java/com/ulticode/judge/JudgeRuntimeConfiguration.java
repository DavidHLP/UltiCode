package com.ulticode.judge;

import com.ulticode.app.config.AppClockConfig;
import com.ulticode.app.config.AppRedisTemplateConfig;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.inspector.DefaultQueueInspector;
import com.ulticode.modules.queue.migration.JudgeStreamLegacyMigration;
import com.ulticode.modules.queue.outbox.reaper.UnackedStreamEntriesReaper;
import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.source.ConfiguredJudgingCaseSource;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.queue.port.adapter.ProblemExampleJudgingCaseSource;
import com.ulticode.modules.queue.port.adapter.TestCaseJudgingCaseSource;
import com.ulticode.modules.queue.processor.DefaultJudgeAttemptExecutor;
import com.ulticode.modules.queue.processor.JudgeWorkerProcessor;
import com.ulticode.modules.queue.processor.JudgeWorkerReadinessHeartbeat;
import com.ulticode.modules.queue.service.impl.QueueServiceImpl;
import com.ulticode.modules.submission.config.DockerSandboxConfigRegistrar;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.config.FlagCombinationValidator;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import com.ulticode.modules.submission.port.DefaultJudgeFeatureFlagsPort;
import com.ulticode.modules.submission.port.DefaultJudgingLanguageSupport;
import com.ulticode.modules.submission.port.adapter.JudgeConfigAdapter;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import com.ulticode.modules.submission.service.impl.DFormEnvelopeCodecImpl;
import com.ulticode.modules.submission.service.impl.SandboxOutputFormatterImpl;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Explicit judge-only bean graph. The storage-free judge runtime provides the
 * queue/sandbox implementation; this import list prevents App controllers,
 * mappers, datasource config, and owner services from being discovered in the
 * worker JVM.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
        "com.ulticode.judge",
        "com.ulticode.modules.submission.sandbox"
})
@Import({
        AppClockConfig.class,
        AppRedisTemplateConfig.class,
        DockerSandboxConfigRegistrar.class,
        FeatureFlagsProperties.class,
        FlagCombinationValidator.class,
        JudgeSourceProperties.class,
        QueueConfig.class,
        QueueServiceImpl.class,
        DefaultQueueInspector.class,
        VerdictMetricsParser.class,
        DefaultJudgingLanguageSupport.class,
        DefaultJudgeFeatureFlagsPort.class,
        JudgeConfigAdapter.class,
        CodeExecutionService.class,
        VerdictResolver.class,
        DFormEnvelopeCodecImpl.class,
        SandboxOutputFormatterImpl.class,
        TestCaseJudgingCaseSource.class,
        ProblemExampleJudgingCaseSource.class,
        ConfiguredJudgingCaseSource.class,
        DefaultJudgeExecutionPipeline.class,
        DefaultJudgeAttemptExecutor.class,
        JudgeWorkerProcessor.class,
        JudgeWorkerReadinessHeartbeat.class,
        UnackedStreamEntriesReaper.class,
        JudgeStreamLegacyMigration.class
})
public class JudgeRuntimeConfiguration {

    /**
     * The worker runs with web-application-type=none and without spring-web on
     * the classpath, so Spring Boot's Jackson auto-configuration cannot back the
     * ObjectMapper bean (JacksonObjectMapperConfiguration requires
     * Jackson2ObjectMapperBuilder). The queue/sandbox pipeline
     * (DFormEnvelopeCodecImpl, judge queue adapters) injects ObjectMapper for
     * JSON envelopes and harness input; provide it explicitly.
     */
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Match Spring Boot's default: the judge envelope carries
                // derived getters (e.g. JudgeJobEnvelope#isFenceAware) that
                // are serialized but are not wire fields. A strict mapper
                // rejects every v2 entry as a poison message, which the
                // fused app-web build never hit because Boot's
                // JacksonAutoConfiguration disables FAIL_ON_UNKNOWN_PROPERTIES.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}

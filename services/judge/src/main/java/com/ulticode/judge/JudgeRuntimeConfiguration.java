package com.ulticode.judge;

import com.ulticode.app.config.AppClockConfig;
import com.ulticode.app.config.AppRedisTemplateConfig;
import com.ulticode.app.uuid.AppUuidGenerator;
import com.ulticode.app.uuid.CommonUuidGeneratorAdapter;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.inspector.DefaultQueueInspector;
import com.ulticode.modules.queue.outbox.reaper.UnackedStreamEntriesReaper;
import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.source.ConfiguredJudgingCaseSource;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.queue.port.adapter.ProblemExampleJudgingCaseSource;
import com.ulticode.modules.queue.port.adapter.TestCaseJudgingCaseSource;
import com.ulticode.modules.queue.processor.DefaultJudgeAttemptExecutor;
import com.ulticode.modules.queue.processor.JudgeWorkerProcessor;
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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Explicit judge-only bean graph. The dependency on backend-app-web provides
 * the existing deep queue/sandbox implementation; this import list prevents
 * App controllers, mappers, datasource config, and owner services from being
 * discovered in the worker JVM.
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
        AppUuidGenerator.ProdAppUuidGenerator.class,
        CommonUuidGeneratorAdapter.class,
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
        UnackedStreamEntriesReaper.class
})
public class JudgeRuntimeConfiguration {
}

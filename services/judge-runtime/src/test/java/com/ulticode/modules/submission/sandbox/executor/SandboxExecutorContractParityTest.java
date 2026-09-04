package com.ulticode.modules.submission.sandbox.executor;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.port.DefaultJudgingLanguageSupport;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import com.ulticode.modules.submission.sandbox.adapter.InMemorySandboxAdapter;
import com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier;
import com.ulticode.modules.submission.service.DFormEnvelopeCodec;
import com.ulticode.modules.submission.service.SandboxOutputFormatter;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the shared {@link SandboxExecutor} contract across the available
 * production and offline adapters without starting a Docker daemon.
 */
class SandboxExecutorContractParityTest {

    private static final List<TestCase> CASES = List.of(
            new TestCase("case-1", "First", List.of(), "1"),
            new TestCase("case-2", "Second", List.of(), "2"));

    @Test
    void dockerAndInMemoryRejectUnknownLanguagesWithSameStatusAndCardinality() {
        SandboxExecutor docker = new SandboxExecutorImpl(
                List.of(), mock(DockerSandboxConfig.class),
                mock(DFormEnvelopeCodec.class), mock(SandboxOutputFormatter.class),
                new SandboxOutcomeClassifier(), mock(ProcessLifecycleRunner.class));
        SandboxExecutor inMemory = new InMemorySandboxAdapter(
                new DefaultJudgingLanguageSupport());

        List<RunCaseResult> dockerResults = docker.runBatch(job(), CASES).cases();
        List<RunCaseResult> inMemoryResults = inMemory.runBatch(job(), CASES).cases();

        assertThat(dockerResults).hasSize(CASES.size())
                .extracting(RunCaseResult::status)
                .containsExactly(SubmissionStatus.SANDBOX_ERROR, SubmissionStatus.SANDBOX_ERROR);
        assertThat(inMemoryResults).hasSize(CASES.size())
                .extracting(RunCaseResult::status)
                .containsExactlyElementsOf(dockerResults.stream()
                        .map(RunCaseResult::status)
                        .toList());
    }

    private static SandboxJob job() {
        return new SandboxJob(
                "run-1", "user-1", "submission-1", 1L,
                "ruby", "puts 'hello'", 2, 256);
    }
}

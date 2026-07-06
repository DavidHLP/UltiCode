package com.ulticode.modules.queue.processor;

import com.ulticode.common.config.FeatureFlagsProperties;
import com.ulticode.common.config.JudgeSourceProperties;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-1 fail-closed guard: when a problem has zero judging-eligible cases
 * (empty {@code test_cases}, or only illegal {@code true,true} / draft
 * {@code false,false} rows), the worker MUST write a System Error verdict and
 * NEVER silently fall back to {@code problem_examples}.
 *
 * <p>Marked {@code IT} so the project's {@code *IT} Maven Surefire rule picks
 * it up. The test body is a Mockito unit (no Testcontainers) — what matters
 * is the fail-closed invariant on the worker's call to
 * {@code submissionService.updateSubmissionResult}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeWorker fail-closed (zero eligible cases)")
class JudgeWorkerFailClosedIT {

    @Mock private QueueService queueService;
    @Mock private CodeExecutionService codeExecutionService;
    @Mock private SubmissionService submissionService;
    @Mock private SubmissionResultPushPort submissionResultPushPort;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private ProblemExampleMapper problemExampleMapper;
    @Mock private TestCaseMapper testCaseMapper;
    @Mock private QueueConfig queueConfig;
    @Mock private ObjectMapper objectMapper;
    @Mock private VerdictResolver verdictResolver;
    @Mock private SubmissionMapper submissionMapper;
    @Mock private FeatureFlagsProperties featureFlags;
    @Mock private MeterRegistry meterRegistry;
    @Mock private ObjectProvider<JudgeQueue> judgeQueueProvider;

    private JudgeWorkerProcessor worker;

    @BeforeEach
    void setUp() {
        // judgeSourceProperties is a real instance (not a mock) so we can
        // flip the flag per test without lenient stubbing noise.
        JudgeSourceProperties judgeSourceProperties = new JudgeSourceProperties();
        judgeSourceProperties.setUseTestCases(true);

        worker = new JudgeWorkerProcessor(
                queueService, codeExecutionService, submissionService, submissionResultPushPort,
                contestSubmissionMapper, problemExampleMapper, testCaseMapper,
                judgeSourceProperties, queueConfig, objectMapper, verdictResolver,
                new com.ulticode.modules.queue.port.VerdictMetricsParser(),
                submissionMapper, featureFlags, meterRegistry, judgeQueueProvider);
    }

    /**
     * Zero judging-eligible cases → System Error; never enqueues code execution
     * and never reads from {@code problem_examples}.
     */
    @Test
    @DisplayName("Empty test_cases → System Error verdict; problem_examples not consulted")
    void emptyTestCasesFailsClosed() {
        JudgeJob job = new JudgeJob();
        job.setSubmissionId("sub-1");
        job.setProblemId("100");
        job.setUserId("u-1");
        job.setLanguage("java");
        job.setCode("class Solution {}");

        when(testCaseMapper.findActiveCasesForJudging(100L)).thenReturn(Collections.emptyList());

        when(featureFlags.isUseGenerationFence()).thenReturn(false); worker.processJob(job);

        // Critical: System Error verdict written.
        ArgumentCaptor<String> verdictCaptor = ArgumentCaptor.forClass(String.class);
        verify(submissionService).updateSubmissionResult(eq("sub-1"), verdictCaptor.capture(),
                anyInt(), anyDouble(), any());
        assertThat(verdictCaptor.getValue()).isEqualTo("System Error");

        // Critical: code execution NEVER invoked (no point running sandbox with no cases).
        verify(codeExecutionService, never()).execute(any(), anyLong(), anyString());
        // Critical: problem_examples NEVER consulted (no silent fallback).
        verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
    }

    /**
     * Even if problem_examples happens to exist, the test_cases path must not
     * consult them as a fallback when no test_cases qualify.
     */
    @Test
    @DisplayName("Non-empty problem_examples does NOT rescue empty test_cases")
    void problemExamplesDoNotRescueEmptyTestCases() {
        JudgeJob job = new JudgeJob();
        job.setSubmissionId("sub-2");
        job.setProblemId("101");
        job.setUserId("u-2");
        job.setLanguage("java");
        job.setCode("class Solution {}");

        when(testCaseMapper.findActiveCasesForJudging(101L)).thenReturn(Collections.emptyList());

        when(featureFlags.isUseGenerationFence()).thenReturn(false); worker.processJob(job);

        ArgumentCaptor<String> verdictCaptor = ArgumentCaptor.forClass(String.class);
        verify(submissionService).updateSubmissionResult(eq("sub-2"), verdictCaptor.capture(),
                anyInt(), anyDouble(), any());
        assertThat(verdictCaptor.getValue()).isEqualTo("System Error");
        verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
        verify(codeExecutionService, never()).execute(any(), anyLong(), anyString());
    }
}

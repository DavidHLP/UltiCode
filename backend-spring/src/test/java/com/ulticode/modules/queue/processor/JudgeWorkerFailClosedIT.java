package com.ulticode.modules.queue.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.config.JudgeSourceProperties;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1 fail-closed guard (now expressed at the pipeline seam after the
 * arch-review deepening): when a problem has zero judging-eligible cases
 * (empty {@code test_cases}, or only illegal {@code true,true} / draft
 * {@code false,false} rows), the pipeline MUST return {@code null} so the
 * caller writes a System Error verdict and NEVER silently falls back to
 * {@code problem_examples}.
 *
 * <p>The test body is a Mockito unit (no Testcontainers) — what matters
 * is the fail-closed invariant on the pipeline's branching.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeExecutionPipeline fail-closed (zero eligible cases)")
class JudgeWorkerFailClosedIT {

    @Mock private TestCaseMapper testCaseMapper;
    @Mock private ProblemExampleMapper problemExampleMapper;
    @Mock private CodeExecutionService codeExecutionService;

    @Spy private JudgeSourceProperties judgeSourceProperties = new JudgeSourceProperties();
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Spy private VerdictResolver verdictResolver = new VerdictResolver();
    @Spy private VerdictMetricsParser verdictMetricsParser = new VerdictMetricsParser();

    @InjectMocks
    private DefaultJudgeExecutionPipeline pipeline;

    /**
     * Zero judging-eligible cases → returns null; caller writes System Error.
     * Sandbox is never invoked (no point running with no cases) and
     * {@code problem_examples} is never consulted (no silent fallback).
     */
    @Test
    @DisplayName("Empty test_cases → null result (System Error at worker); problem_examples not consulted")
    void emptyTestCasesFailsClosed() throws Exception {
        judgeSourceProperties.setUseTestCases(true);
        when(testCaseMapper.findActiveCasesForJudging(100L)).thenReturn(Collections.emptyList());

        JudgeExecutionResult result = pipeline.execute(
                "java", "class Solution {}", 100L, "u-1", "sub-1");

        // Critical: pipeline returns null so the worker writes System Error.
        assertThat(result).isNull();

        // Critical: code execution NEVER invoked (no point running sandbox with no cases).
        verify(codeExecutionService, never()).execute(any(), anyLong(), anyString());
        // Critical: problem_examples NEVER consulted (no silent fallback).
        verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
    }

    /**
     * Even if {@code problem_examples} happens to exist, the test_cases path
     * must not consult them as a fallback when no test_cases qualify.
     */
    @Test
    @DisplayName("Non-empty problem_examples does NOT rescue empty test_cases")
    void problemExamplesDoNotRescueEmptyTestCases() throws Exception {
        judgeSourceProperties.setUseTestCases(true);
        when(testCaseMapper.findActiveCasesForJudging(101L)).thenReturn(Collections.emptyList());

        JudgeExecutionResult result = pipeline.execute(
                "java", "class Solution {}", 101L, "u-2", "sub-2");

        assertThat(result).isNull();
        verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
        verify(codeExecutionService, never()).execute(any(), anyLong(), anyString());
    }
}
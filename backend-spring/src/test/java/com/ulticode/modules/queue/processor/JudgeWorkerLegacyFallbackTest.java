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
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-1: explicit regression guard for the {@code flag=false} legacy path.
 *
 * <p>When {@code app.features.judge-source.use-test-cases=false} the worker
 * must continue to source cases from {@code problem_examples} and produce
 * {@code TestCaseDetail} rows whose {@code caseScope} and {@code caseId} are
 * both {@code null} (so the user-facing projection treats them as legacy
 * sample). This path is slated for deletion in Phase 3 (task #7) but must
 * keep working unchanged until then so the rollback drill is a no-op.
 *
 * <p>Pure Mockito unit (no Testcontainers). Marked as {@code Test} (not
 * {@code IT}) because it doesn't need the {@code *IT} Surefire rule; it is
 * a behavioural guard for the rollback path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeWorker legacy fallback (flag=false)")
class JudgeWorkerLegacyFallbackTest {

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

    @Test
    @DisplayName("flag=false: legacy path produces scope-null details and never consults test_cases")
    void legacyFallbackLeavesScopeNull() {
        JudgeSourceProperties props = new JudgeSourceProperties();
        props.setUseTestCases(false);
        JudgeWorkerProcessor worker = new JudgeWorkerProcessor(
                queueService, codeExecutionService, submissionService, submissionResultPushPort,
                contestSubmissionMapper, problemExampleMapper, testCaseMapper,
                props, queueConfig, objectMapper, verdictResolver,
                new com.ulticode.modules.queue.port.VerdictMetricsParser(),
                submissionMapper, featureFlags, meterRegistry, judgeQueueProvider);

        JudgeJob j = new JudgeJob();
        j.setSubmissionId("sub-legacy");
        j.setProblemId("100");
        j.setUserId("u-1");
        j.setLanguage("java");
        j.setCode("class Solution {}");

        ProblemExample ex = new ProblemExample();
        ex.setId("1");
        ex.setProblemId(100L);
        ex.setExampleOrder(1);
        ex.setInputText("stdin");
        ex.setOutputText("expected");
        when(problemExampleMapper.findByProblemIdOrderByOrder(100L)).thenReturn(List.of(ex));

        RunResultDTO result = RunResultDTO.builder()
                .cases(List.of(RunResultDTO.RunCaseResult.builder()
                        .testCaseId("1")
                        .status("Accepted")
                        .runtime("10").memory("1.5")
                        .output("out").expectedOutput("exp").detail(null).build()))
                .passedCases(1).totalCases(1).errorMessage(null)
                .build();
        when(codeExecutionService.execute(any(RunSubmissionDTO.class), eq(100L), eq("u-1")))
                .thenReturn(result);
        when(verdictResolver.reduceWire(anyList())).thenReturn(
                com.ulticode.modules.submission.enums.SubmissionStatus.ACCEPTED);

        when(featureFlags.isUseGenerationFence()).thenReturn(false);
        worker.processJob(j);

        ArgumentCaptor<List<Submission.TestCaseDetail>> detailsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(submissionService).updateSubmissionResult(eq("sub-legacy"), anyString(),
                anyInt(), anyDouble(), detailsCaptor.capture());

        List<Submission.TestCaseDetail> written = detailsCaptor.getValue();
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getCaseScope()).isNull();
        assertThat(written.get(0).getCaseId()).isNull();

        // test_cases must NOT be consulted on the rollback path.
        verify(testCaseMapper, never()).findActiveCasesForJudging(anyLong());
        // problem_examples IS consulted on the rollback path.
        verify(problemExampleMapper, times(1)).findByProblemIdOrderByOrder(100L);
    }
}

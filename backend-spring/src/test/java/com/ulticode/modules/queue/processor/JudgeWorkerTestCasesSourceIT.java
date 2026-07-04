package com.ulticode.modules.queue.processor;

import com.ulticode.common.config.FeatureFlagsProperties;
import com.ulticode.common.config.JudgeSourceProperties;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
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
 * P0-1: confirms {@code JudgeWorkerProcessor} picks the right source based on
 * the {@code app.features.judge-source.use-test-cases} flag, and that the
 * {@code test_cases} path actually stamps {@code caseId} + {@code caseScope}
 * onto the resulting {@code TestCaseDetail} list passed to
 * {@code updateSubmissionResult}.
 *
 * <p>Mockito unit (no Testcontainers): we capture the details written back
 * and assert scope metadata — the actual database query shape is exercised
 * in {@code TestCaseSoftDeleteFilterIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeWorker source routing")
class JudgeWorkerTestCasesSourceIT {

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

    private JudgeWorkerProcessor buildWorker(boolean useTestCases) {
        JudgeSourceProperties props = new JudgeSourceProperties();
        props.setUseTestCases(useTestCases);
        return new JudgeWorkerProcessor(
                queueService, codeExecutionService, submissionService, submissionResultPushPort,
                contestSubmissionMapper, problemExampleMapper, testCaseMapper,
                props, queueConfig, objectMapper, verdictResolver,
                submissionMapper, featureFlags, meterRegistry, judgeQueueProvider);
    }

    private JudgeJob job(String problemId) {
        JudgeJob j = new JudgeJob();
        j.setSubmissionId("sub-" + problemId);
        j.setProblemId(problemId);
        j.setUserId("u-1");
        j.setLanguage("java");
        j.setCode("class Solution {}");
        return j;
    }

    private TestCase tc(String id, boolean sample, boolean hidden, int order) {
        TestCase t = new TestCase();
        t.setId(id);
        t.setProblemId(Long.parseLong("100"));
        t.setIsSample(sample);
        t.setIsHidden(hidden);
        t.setTestOrder(order);
        t.setInputText("stdin-" + id);
        t.setOutputText("expected-" + id);
        return t;
    }

    private RunResultDTO.RunCaseResult cr(String testCaseId, String status) {
        return RunResultDTO.RunCaseResult.builder()
                .testCaseId(testCaseId)
                .status(status)
                .runtime("10")
                .memory("1.5")
                .output("out")
                .expectedOutput("exp")
                .detail(null)
                .build();
    }

    @Test
    @DisplayName("flag=true: worker reads test_cases, writes caseId/caseScope per detail")
    void flagTrueReadsTestCasesAndWritesScope() {
        JudgeWorkerProcessor worker = buildWorker(true);
        JudgeJob j = job("100");

        List<TestCase> cases = List.of(
                tc("tc-s-1", true, false, 1),
                tc("tc-h-1", false, true, 2));
        when(testCaseMapper.findActiveCasesForJudging(100L)).thenReturn(cases);
        // Sandbox echoes testCaseId back per case; we simulate the matching.
        RunResultDTO result = RunResultDTO.builder()
                .cases(List.of(cr("tc-s-1", "Accepted"), cr("tc-h-1", "Accepted")))
                .passedCases(2).totalCases(2).errorMessage(null)
                .build();
        when(codeExecutionService.execute(any(RunSubmissionDTO.class), eq(100L), eq("u-1")))
                .thenReturn(result);
        when(verdictResolver.reduceWire(anyList())).thenReturn(
                com.ulticode.modules.submission.enums.SubmissionStatus.ACCEPTED);

        when(featureFlags.isUseGenerationFence()).thenReturn(false);
        worker.processJob(j);

        ArgumentCaptor<List<Submission.TestCaseDetail>> detailsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(submissionService).updateSubmissionResult(eq("sub-100"), anyString(),
                anyInt(), anyDouble(), detailsCaptor.capture());

        List<Submission.TestCaseDetail> written = detailsCaptor.getValue();
        assertThat(written).hasSize(2);
        Submission.TestCaseDetail sampleDetail = written.stream()
                .filter(d -> "tc-s-1".equals(d.getCaseId())).findFirst().orElseThrow();
        Submission.TestCaseDetail hiddenDetail = written.stream()
                .filter(d -> "tc-h-1".equals(d.getCaseId())).findFirst().orElseThrow();
        assertThat(sampleDetail.getCaseScope()).isEqualTo(CaseScope.SAMPLE);
        assertThat(hiddenDetail.getCaseScope()).isEqualTo(CaseScope.HIDDEN);

        // Critical: problem_examples NOT consulted when flag is true.
        verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
    }

    @Test
    @DisplayName("flag=false: worker reads problem_examples, caseScope stays null (legacy)")
    void flagFalseReadsProblemExamplesAndLeavesScopeNull() {
        JudgeWorkerProcessor worker = buildWorker(false);
        JudgeJob j = job("100");

        ProblemExample ex = new ProblemExample();
        ex.setId("1");
        ex.setProblemId(100L);
        ex.setExampleOrder(1);
        ex.setInputText("stdin");
        ex.setOutputText("expected");
        when(problemExampleMapper.findByProblemIdOrderByOrder(100L)).thenReturn(List.of(ex));

        RunResultDTO result = RunResultDTO.builder()
                .cases(List.of(cr(null, "Accepted")))
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
        verify(submissionService).updateSubmissionResult(eq("sub-100"), anyString(),
                anyInt(), anyDouble(), detailsCaptor.capture());

        // Legacy path: no scope metadata. Projection layer will treat null as
        // legacy sample, so legacy rows stay user-visible.
        List<Submission.TestCaseDetail> written = detailsCaptor.getValue();
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getCaseScope()).isNull();
        assertThat(written.get(0).getCaseId()).isNull();

        // Critical: test_cases NOT consulted when flag is false (rollback path).
        verify(testCaseMapper, never()).findActiveCasesForJudging(anyLong());
    }
}

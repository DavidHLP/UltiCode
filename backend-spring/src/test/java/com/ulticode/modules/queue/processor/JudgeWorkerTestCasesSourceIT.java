package com.ulticode.modules.queue.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1: confirms the pipeline picks the right source based on
 * {@code app.features.judge-source.use-test-cases}, and that the
 * {@code test_cases} path actually stamps {@code caseId} + {@code caseScope}
 * onto the resulting {@link Submission.TestCaseDetail} list.
 *
 * <p>The pipeline (now the SUT after the arch-review deepening) is the
 * surface that owns source routing; the worker just consumes the
 * {@link JudgeExecutionResult}. The test asserts that
 * {@code flag=true} ⇒ SAMPLE / HIDDEN scopes are stamped, and {@code flag=false}
 * ⇒ scopes stay null (legacy rollback path).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeExecutionPipeline source routing")
class JudgeWorkerTestCasesSourceIT {

    @Mock private TestCaseMapper testCaseMapper;
    @Mock private ProblemExampleMapper problemExampleMapper;
    @Mock private CodeExecutionService codeExecutionService;

    @Spy private JudgeSourceProperties judgeSourceProperties = new JudgeSourceProperties();
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Spy private VerdictResolver verdictResolver = new VerdictResolver();
    @Spy private VerdictMetricsParser verdictMetricsParser = new VerdictMetricsParser();

    @InjectMocks
    private DefaultJudgeExecutionPipeline pipeline;

    private TestCase tc(String id, boolean sample, boolean hidden, int order) {
        TestCase t = new TestCase();
        t.setId(id);
        t.setProblemId(100L);
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
    @DisplayName("flag=true: pipeline reads test_cases, writes caseId/caseScope per detail")
    void flagTrueReadsTestCasesAndWritesScope() throws Exception {
        judgeSourceProperties.setUseTestCases(true);

        List<TestCase> cases = List.of(
                tc("tc-s-1", true, false, 1),
                tc("tc-h-1", false, true, 2));
        when(testCaseMapper.findActiveCasesForJudging(100L)).thenReturn(cases);
        // Sandbox echoes testCaseId back per case; we simulate the matching.
        RunResultDTO result = RunResultDTO.builder()
                .cases(List.of(cr("tc-s-1", "Accepted"), cr("tc-h-1", "Accepted")))
                .passedCases(2).totalCases(2).errorMessage(null)
                .build();
        when(codeExecutionService.execute(any(), eq(100L), eq("u-1"))).thenReturn(result);

        JudgeExecutionResult executionResult =
                pipeline.execute("java", "class Solution {}", 100L, "u-1", "sub-100");

        List<Submission.TestCaseDetail> written = executionResult.testCaseDetails();
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
    @DisplayName("flag=false: pipeline reads problem_examples, caseScope stays null (legacy)")
    void flagFalseReadsProblemExamplesAndLeavesScopeNull() throws Exception {
        judgeSourceProperties.setUseTestCases(false);

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
        when(codeExecutionService.execute(any(), eq(100L), eq("u-1"))).thenReturn(result);

        JudgeExecutionResult executionResult =
                pipeline.execute("java", "class Solution {}", 100L, "u-1", "sub-100");

        // Legacy path: no scope metadata. Projection layer will treat null as
        // legacy sample, so legacy rows stay user-visible.
        List<Submission.TestCaseDetail> written = executionResult.testCaseDetails();
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getCaseScope()).isNull();
        assertThat(written.get(0).getCaseId()).isNull();

        // Critical: test_cases NOT consulted when flag is false (rollback path).
        verify(testCaseMapper, never()).findActiveCasesForJudging(anyLong());
    }
}
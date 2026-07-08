package com.ulticode.modules.queue.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.entity.Submission;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1: explicit regression guard for the {@code flag=false} legacy path
 * (now expressed at the pipeline seam after the arch-review deepening).
 *
 * <p>When {@code app.features.judge-source.use-test-cases=false} the
 * pipeline must continue to source cases from {@code problem_examples} and
 * produce {@code TestCaseDetail} rows whose {@code caseScope} and
 * {@code caseId} are both {@code null} (so the user-facing projection treats
 * them as legacy sample). This path is slated for deletion in Phase 3 but
 * must keep working unchanged until then so the rollback drill is a no-op.
 *
 * <p>Pure Mockito unit (no Testcontainers). Marked as {@code Test} (not
 * {@code IT}) because it doesn't need the {@code *IT} Surefire rule; it is
 * a behavioural guard for the rollback path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeExecutionPipeline legacy fallback (flag=false)")
class JudgeWorkerLegacyFallbackTest {

    @Mock private TestCaseMapper testCaseMapper;
    @Mock private ProblemExampleMapper problemExampleMapper;
    @Mock private CodeExecutionService codeExecutionService;

    @Spy private JudgeSourceProperties judgeSourceProperties = new JudgeSourceProperties();
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Spy private VerdictResolver verdictResolver = new VerdictResolver();
    @Spy private VerdictMetricsParser verdictMetricsParser = new VerdictMetricsParser();

    @InjectMocks
    private DefaultJudgeExecutionPipeline pipeline;

    @Test
    @DisplayName("flag=false: legacy path produces scope-null details and never consults test_cases")
    void legacyFallbackLeavesScopeNull() throws Exception {
        judgeSourceProperties.setUseTestCases(false);

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
        when(codeExecutionService.execute(any(), eq(100L), eq("u-1"))).thenReturn(result);

        var executionResult = pipeline.execute("java", "class Solution {}", 100L, "u-1", "sub-legacy");

        List<Submission.TestCaseDetail> written = executionResult.testCaseDetails();
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getCaseScope()).isNull();
        assertThat(written.get(0).getCaseId()).isNull();

        // test_cases must NOT be consulted on the rollback path.
        verify(testCaseMapper, never()).findActiveCasesForJudging(anyLong());
        // problem_examples IS consulted on the rollback path.
        verify(problemExampleMapper, times(1)).findByProblemIdOrderByOrder(100L);
    }
}
package com.ulticode.modules.queue.processor;

import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.app.api.dto.RunResultDTO;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1: regression guard for the legacy (flag=false) judging path.
 *
 * <p>After the test-case-source seam deepening, source selection (canonical
 * {@code test_cases} vs legacy {@code problem_examples}) lives behind
 * {@link JudgingCaseSource} — covered by {@code ConfiguredJudgingCaseSourceTest}
 * — and the pipeline depends only on that seam. The legacy adapter
 * ({@code ProblemExampleJudgingCaseSource}) emits {@link JudgingCase} rows
 * whose {@code hidden}/{@code sample} flags are both {@code null}; this test
 * pins the pipeline contract for those legacy rows so the rollback path stays a
 * no-op: per-case {@code caseScope} stays unset and {@code caseId} is stamped
 * from the seam-supplied id.
 *
 * <p>Pure Mockito unit (no Testcontainers). Marked as {@code Test} (not
 * {@code IT}) because it doesn't need the {@code *IT} Surefire rule; it is
 * a behavioural guard for the rollback path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeExecutionPipeline legacy fallback (flag=false)")
class JudgeWorkerLegacyFallbackTest {

    // The pipeline's only case-loading collaborator; source selection (canonical
    // vs legacy) is owned by ConfiguredJudgingCaseSource and covered by its own
    // test. Wiring matches DefaultJudgeExecutionPipelineTest so @InjectMocks
    // populates every constructor parameter (no null JudgingCaseSource).
    @Mock private JudgingCaseSource judgingCaseSource;
    @Mock private CodeExecutionService codeExecutionService;

    @Spy private VerdictResolver verdictResolver = new VerdictResolver();
    @Spy private VerdictMetricsParser verdictMetricsParser = new VerdictMetricsParser();

    @InjectMocks
    private DefaultJudgeExecutionPipeline pipeline;

    @Test
    @DisplayName("flag=false: legacy cases (null flags) produce scope-null details via the JudgingCaseSource seam")
    void legacyFallbackLeavesScopeNull() throws Exception {
        // Legacy ProblemExampleJudgingCaseSource rows carry null hidden/sample flags.
        JudgingCase legacyCase = new JudgingCase("1", "Case 1", "expected", List.of(), null, null);
        when(judgingCaseSource.loadCases(100L)).thenReturn(List.of(legacyCase));

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

        assertThat(executionResult).isNotNull();
        List<Submission.TestCaseDetail> written = executionResult.testCaseDetails();
        assertThat(written).hasSize(1);
        // Legacy rows have no hidden/sample flag → caseScope stays null.
        assertThat(written.get(0).getCaseScope()).isNull();
        // caseId is stamped uniformly from the seam-supplied JudgingCase id for
        // BOTH sources; a null caseId is no longer a "legacy submission" marker.
        assertThat(written.get(0).getCaseId()).isEqualTo("1");

        // The pipeline sources cases only through the seam; it never touches the
        // Problem module's mappers or the source-selection flag directly.
        verify(judgingCaseSource).loadCases(100L);
    }
}

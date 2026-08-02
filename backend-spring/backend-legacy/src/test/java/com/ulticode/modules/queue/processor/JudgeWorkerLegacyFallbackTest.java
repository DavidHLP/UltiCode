package com.ulticode.modules.queue.processor;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.entity.Submission;
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
 * <p>Source selection (canonical {@code test_cases} vs legacy
 * {@code problem_examples}) lives behind {@link JudgingCaseSource}. The legacy
 * adapter emits {@link JudgingCase} rows whose {@code hidden}/{@code sample}
 * flags are both {@code null}; this test pins the pipeline contract so the
 * rollback path keeps case scope unset and stamps the seam-supplied case id.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P0-1 JudgeExecutionPipeline legacy fallback (flag=false)")
class JudgeWorkerLegacyFallbackTest {

    @Mock
    private JudgingCaseSource judgingCaseSource;

    @Mock
    private CodeExecutionPort codeExecutionPort;

    @Spy
    private VerdictResolver verdictResolver = new VerdictResolver();

    @Spy
    private VerdictMetricsParser verdictMetricsParser = new VerdictMetricsParser();

    @InjectMocks
    private DefaultJudgeExecutionPipeline pipeline;

    @Test
    @DisplayName("flag=false: legacy cases produce scope-null details via the JudgingCaseSource seam")
    void legacyFallbackLeavesScopeNull() throws Exception {
        JudgingCase legacyCase = new JudgingCase("1", "Case 1", "expected", List.of(), null, null);
        when(judgingCaseSource.loadCases(100L)).thenReturn(List.of(legacyCase));

        RunResultDTO result = RunResultDTO.builder()
                .cases(List.of(RunResultDTO.RunCaseResult.builder()
                        .testCaseId("1")
                        .status("Accepted")
                        .runtime("10")
                        .memory("1.5")
                        .output("out")
                        .expectedOutput("exp")
                        .detail(null)
                        .build()))
                .passedCases(1)
                .totalCases(1)
                .errorMessage(null)
                .build();
        when(codeExecutionPort.execute(any(), eq(100L), eq("u-1"))).thenReturn(result);

        var executionResult = pipeline.execute(
                "java", "class Solution {}", 100L, "u-1", "sub-legacy");

        assertThat(executionResult).isNotNull();
        List<Submission.TestCaseDetail> written = executionResult.testCaseDetails();
        assertThat(written).hasSize(1);
        assertThat(written.get(0).getCaseScope()).isNull();
        assertThat(written.get(0).getCaseId()).isEqualTo("1");
        verify(judgingCaseSource).loadCases(100L);
    }
}

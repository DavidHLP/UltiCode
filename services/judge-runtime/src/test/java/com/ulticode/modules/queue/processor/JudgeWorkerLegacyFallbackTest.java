package com.ulticode.modules.queue.processor;

import com.ulticode.modules.submission.runtime.JudgeRunResponse;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.queue.pipeline.DefaultJudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeTestCaseDetail;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
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
@DisplayName("JudgeExecutionPipeline legacy case-source fallback")
class JudgeWorkerLegacyFallbackTest {

    @Mock
    private JudgingCaseSource judgingCaseSource;

    @Mock
    private CodeExecutionService codeExecutionPort;

    @Spy
    private VerdictResolver verdictResolver = new VerdictResolver();

    @Spy
    private VerdictMetricsParser verdictMetricsParser = new VerdictMetricsParser();

    @InjectMocks
    private DefaultJudgeExecutionPipeline pipeline;

    @Test
    @DisplayName("legacy cases produce scope-null details via the JudgingCaseSource seam")
    void legacyFallbackLeavesScopeNull() throws Exception {
        JudgingCase legacyCase = new JudgingCase("1", "Case 1", "expected", List.of(), null, null);
        when(judgingCaseSource.loadCases(100L)).thenReturn(List.of(legacyCase));

        JudgeRunResponse result = JudgeRunResponse.builder()
                .cases(List.of(JudgeRunResponse.RunCaseResult.builder()
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
        List<JudgeTestCaseDetail> written = executionResult.testCaseDetails();
        assertThat(written).hasSize(1);
        assertThat(written.get(0).caseScope()).isNull();
        assertThat(written.get(0).caseId()).isEqualTo("1");
        verify(judgingCaseSource).loadCases(100L);
    }
}

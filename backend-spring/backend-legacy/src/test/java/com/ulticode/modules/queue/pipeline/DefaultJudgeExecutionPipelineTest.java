package com.ulticode.modules.queue.pipeline;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.service.VerdictResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pipeline-level tests after the test-case-source seam deepening.
 *
 * <p>The {@link DefaultJudgeExecutionPipeline} depends only on the
 * {@link JudgingCaseSource} seam and app-api execution port. These tests feed
 * judge-ready {@link JudgingCase} instances directly, exercising sandbox
 * dispatch, verdict reduction, metric extraction, and per-case detail shaping.
 *
 * <p>Source selection (canonical vs legacy) is covered by
 * {@code ConfiguredJudgingCaseSourceTest}; mapper-to-{@link JudgingCase}
 * mapping is covered by the adapter tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultJudgeExecutionPipeline")
class DefaultJudgeExecutionPipelineTest {

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

    @Nested
    @DisplayName("execute (happy paths)")
    class HappyPaths {

        @Test
        @DisplayName("stamps caseId + caseScope from JudgingCase flags, returns Accepted")
        void happyPathWithFlaggedCases() throws Exception {
            JudgingCase sample = judgingCase("tc-s-1", Boolean.TRUE, Boolean.FALSE);
            JudgingCase hidden = judgingCase("tc-h-1", Boolean.FALSE, Boolean.TRUE);
            when(judgingCaseSource.loadCases(100L)).thenReturn(List.of(sample, hidden));
            when(codeExecutionPort.execute(any(), eq(100L), eq("user-1")))
                    .thenReturn(RunResultDTO.builder()
                            .cases(List.of(
                                    caseResult("tc-s-1", "Accepted", "10", "1.5"),
                                    caseResult("tc-h-1", "Accepted", "20", "2.0")))
                            .passedCases(2).totalCases(2).errorMessage(null)
                            .build());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(result.maxRuntimeMs()).isEqualTo(20);
            assertThat(result.maxMemoryMb()).isEqualTo(2.0);
            assertThat(result.testCaseDetails()).hasSize(2);
            Submission.TestCaseDetail sampleDetail = result.testCaseDetails().stream()
                    .filter(detail -> "tc-s-1".equals(detail.getCaseId())).findFirst().orElseThrow();
            Submission.TestCaseDetail hiddenDetail = result.testCaseDetails().stream()
                    .filter(detail -> "tc-h-1".equals(detail.getCaseId())).findFirst().orElseThrow();
            assertThat(sampleDetail.getCaseScope()).isEqualTo(CaseScope.SAMPLE);
            assertThat(hiddenDetail.getCaseScope()).isEqualTo(CaseScope.HIDDEN);
        }

        @Test
        @DisplayName("cases with null flags leave caseScope null (legacy-style), returns Accepted")
        void happyPathWithNullFlagCases() throws Exception {
            JudgingCase legacy = judgingCase("c-1", null, null);
            when(judgingCaseSource.loadCases(100L)).thenReturn(List.of(legacy));
            when(codeExecutionPort.execute(any(), eq(100L), eq("user-1")))
                    .thenReturn(RunResultDTO.builder()
                            .cases(List.of(caseResult("c-1", "Accepted", "10", "1.5")))
                            .passedCases(1).totalCases(1).errorMessage(null)
                            .build());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(result.testCaseDetails()).hasSize(1);
            assertThat(result.testCaseDetails().get(0).getCaseScope()).isNull();
            assertThat(result.testCaseDetails().get(0).getCaseId()).isEqualTo("c-1");
        }
    }

    @Nested
    @DisplayName("execute (empty cases)")
    class EmptyCases {

        @Test
        @DisplayName("empty cases → returns null (fail closed, no fallback)")
        void emptyCasesReturnsNull() throws Exception {
            when(judgingCaseSource.loadCases(100L)).thenReturn(List.of());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNull();
            verify(codeExecutionPort, never()).execute(any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("null cases → returns null (fail closed)")
        void nullCasesReturnsNull() throws Exception {
            when(judgingCaseSource.loadCases(100L)).thenReturn(null);

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNull();
            verify(codeExecutionPort, never()).execute(any(), anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("execute (exception propagation)")
    class ExceptionPropagation {

        @Test
        @DisplayName("sandbox exception propagates (caller decides System Error)")
        void sandboxExceptionPropagates() {
            when(judgingCaseSource.loadCases(100L))
                    .thenReturn(List.of(judgingCase("tc-1", Boolean.TRUE, Boolean.FALSE)));
            when(codeExecutionPort.execute(any(), eq(100L), eq("user-1")))
                    .thenThrow(new RuntimeException("sandbox unreachable"));

            assertThatThrownBy(() -> pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("sandbox unreachable");
        }
    }

    @Nested
    @DisplayName("determineVerdict (case-level reduction)")
    class DetermineVerdict {

        @Test
        @DisplayName("returns Runtime Error when any case has RE (highest priority)")
        void runtimeErrorHasHighestPriority() {
            var cases = List.of(
                    caseResult(null, "Accepted", "50ms", "4.0MB"),
                    caseResult(null, "Runtime Error", "100ms", "8.0MB"),
                    caseResult(null, "Accepted", "30ms", "3.0MB"));

            assertThat(pipeline.determineVerdict(cases)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("returns Accepted when all cases pass")
        void allAcceptedReturnsAccepted() {
            var cases = List.of(
                    caseResult(null, "Accepted", "50ms", "4.0MB"),
                    caseResult(null, "Accepted", "30ms", "3.0MB"));

            assertThat(pipeline.determineVerdict(cases)).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("returns Wrong Answer when any case fails with WA")
        void wrongAnswerWhenPresent() {
            var cases = List.of(
                    caseResult(null, "Accepted", "50ms", "4.0MB"),
                    caseResult(null, "Wrong Answer", "30ms", "3.0MB"));

            assertThat(pipeline.determineVerdict(cases)).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        }

        @Test
        @DisplayName("priority order: RE > MLE > TLE > WA > PE > Accepted")
        void fullPriorityOrder() {
            var cases = List.of(
                    caseResult(null, "Accepted", "10ms", "1.0MB"),
                    caseResult(null, "Presentation Error", "10ms", "1.0MB"),
                    caseResult(null, "Wrong Answer", "10ms", "1.0MB"),
                    caseResult(null, "Time Limit Exceeded", "2000ms", "1.0MB"),
                    caseResult(null, "Memory Limit Exceeded", "10ms", "256.0MB"),
                    caseResult(null, "Runtime Error", "10ms", "1.0MB"));

            assertThat(pipeline.determineVerdict(cases)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("empty case list → System Error (reducer convention)")
        void emptyCasesReturnsSystemError() {
            assertThat(pipeline.determineVerdict(List.of())).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        }
    }

    private JudgingCase judgingCase(String id, Boolean sample, Boolean hidden) {
        return new JudgingCase(id, "Case 1", "expected-" + id, List.of(), hidden, sample);
    }

    private RunResultDTO.RunCaseResult caseResult(
            String testCaseId, String status, String runtime, String memory) {
        return RunResultDTO.RunCaseResult.builder()
                .testCaseId(testCaseId)
                .status(status)
                .runtime(runtime)
                .memory(memory)
                .output("out")
                .expectedOutput("exp")
                .detail(null)
                .build();
    }
}

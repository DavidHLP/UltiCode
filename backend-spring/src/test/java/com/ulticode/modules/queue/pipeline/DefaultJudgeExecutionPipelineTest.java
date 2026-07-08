package com.ulticode.modules.queue.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.VerdictResolver;
import org.junit.jupiter.api.BeforeEach;
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
 * Pipeline-level tests after the arch-review deepening.
 *
 * <p>The {@link DefaultJudgeExecutionPipeline} owns the execution path that
 * used to live inside {@code JudgeWorkerProcessor}:
 * <ol>
 *   <li>Load test cases (canonical {@code test_cases} table or legacy
 *       {@code problem_examples})</li>
 *   <li>Build the {@code RunSubmissionDTO} the sandbox consumes</li>
 *   <li>Dispatch to {@link CodeExecutionService}</li>
 *   <li>Reduce per-case verdicts via {@link VerdictResolver}</li>
 *   <li>Extract peak runtime/memory via {@link VerdictMetricsParser}</li>
 *   <li>Stamp {@code caseId} / {@code caseScope} onto
 *       {@link Submission.TestCaseDetail} when the case came from
 *       {@code test_cases}</li>
 * </ol>
 *
 * <p>The {@code JudgeWorkerProcessorTest} exercises the worker's plumbing
 * (polling, lease, push) with the pipeline mocked. This test exercises the
 * pipeline directly so the verdict-resolution + DTO-building logic has its
 * own focused surface.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultJudgeExecutionPipeline")
class DefaultJudgeExecutionPipelineTest {

    @Mock
    private TestCaseMapper testCaseMapper;

    @Mock
    private ProblemExampleMapper problemExampleMapper;

    @Mock
    private CodeExecutionService codeExecutionService;

    /**
     * Real instance so each test can flip the flag. The pipeline is the only
     * consumer of {@link JudgeSourceProperties}, so a real bean here keeps the
     * test free of stubbing noise.
     */
    @Spy
    private JudgeSourceProperties judgeSourceProperties = new JudgeSourceProperties();

    /**
     * Real instance — the pipeline's {@code parseInputs} relies on Jackson's
     * {@code readValue} for the structured inputs JSON. Stubbing a Mock would
     * either hide the parse path or require per-call answers.
     */
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Real instance — {@link VerdictResolver#reduceWire} is a pure reducer and
     * the pipeline's verdict logic is best exercised against the real
     * implementation, not a stub that returns whatever we tell it to.
     */
    @Spy
    private VerdictResolver verdictResolver = new VerdictResolver();

    /**
     * Real instance — same reasoning: the pipeline's peak metric extraction
     * is the seam this parser exists for.
     */
    @Spy
    private VerdictMetricsParser verdictMetricsParser = new VerdictMetricsParser();

    @InjectMocks
    private DefaultJudgeExecutionPipeline pipeline;

    @BeforeEach
    void setUp() {
        // Default to the canonical test_cases path; individual tests opt in
        // to the legacy problem_examples path by calling setUseTestCases(false).
        judgeSourceProperties.setUseTestCases(true);
    }

    // === execute — happy paths ===

    @Nested
    @DisplayName("execute (happy paths)")
    class HappyPaths {

        @Test
        @DisplayName("flag=true: loads test_cases, runs sandbox, stamps caseId + caseScope, returns Accepted")
        void happyPathWithTestCases() throws Exception {
            judgeSourceProperties.setUseTestCases(true);
            TestCase sample = tc("tc-s-1", true, false, 1);
            TestCase hidden = tc("tc-h-1", false, true, 2);
            when(testCaseMapper.findActiveCasesForJudging(100L))
                    .thenReturn(List.of(sample, hidden));
            when(codeExecutionService.execute(any(), eq(100L), eq("user-1")))
                    .thenReturn(RunResultDTO.builder()
                            .cases(List.of(
                                    cr("tc-s-1", "Accepted", "10", "1.5"),
                                    cr("tc-h-1", "Accepted", "20", "2.0")))
                            .passedCases(2).totalCases(2).errorMessage(null)
                            .build());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNotNull();
            assertThat(result.verdict()).isEqualTo("Accepted");
            assertThat(result.maxRuntimeMs()).isEqualTo(20);
            assertThat(result.maxMemoryMb()).isEqualTo(2.0);
            assertThat(result.testCaseDetails()).hasSize(2);
            Submission.TestCaseDetail sampleDetail = result.testCaseDetails().stream()
                    .filter(d -> "tc-s-1".equals(d.getCaseId())).findFirst().orElseThrow();
            Submission.TestCaseDetail hiddenDetail = result.testCaseDetails().stream()
                    .filter(d -> "tc-h-1".equals(d.getCaseId())).findFirst().orElseThrow();
            assertThat(sampleDetail.getCaseScope()).isEqualTo(CaseScope.SAMPLE);
            assertThat(hiddenDetail.getCaseScope()).isEqualTo(CaseScope.HIDDEN);

            verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
        }

        @Test
        @DisplayName("flag=false: loads problem_examples, runs sandbox, leaves caseScope null, returns Accepted")
        void happyPathWithProblemExamples() throws Exception {
            judgeSourceProperties.setUseTestCases(false);
            ProblemExample ex = new ProblemExample();
            ex.setId("1");
            ex.setProblemId(100L);
            ex.setExampleOrder(1);
            ex.setInputText("stdin");
            ex.setOutputText("expected");
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L))
                    .thenReturn(List.of(ex));
            when(codeExecutionService.execute(any(), eq(100L), eq("user-1")))
                    .thenReturn(RunResultDTO.builder()
                            .cases(List.of(cr(null, "Accepted", "10", "1.5")))
                            .passedCases(1).totalCases(1).errorMessage(null)
                            .build());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNotNull();
            assertThat(result.verdict()).isEqualTo("Accepted");
            assertThat(result.testCaseDetails()).hasSize(1);
            assertThat(result.testCaseDetails().get(0).getCaseScope()).isNull();
            assertThat(result.testCaseDetails().get(0).getCaseId()).isNull();

            verify(testCaseMapper, never()).findActiveCasesForJudging(anyLong());
        }
    }

    // === execute — empty cases ===

    @Nested
    @DisplayName("execute (empty cases)")
    class EmptyCases {

        @Test
        @DisplayName("flag=true + empty test_cases → returns null (fail closed, no problem_examples fallback)")
        void flagTrueEmptyTestCases_returnsNull() throws Exception {
            judgeSourceProperties.setUseTestCases(true);
            when(testCaseMapper.findActiveCasesForJudging(100L)).thenReturn(List.of());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNull();
            verify(codeExecutionService, never()).execute(any(), anyLong(), anyString());
            verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
        }

        @Test
        @DisplayName("flag=true + null test_cases → returns null (fail closed)")
        void flagTrueNullTestCases_returnsNull() throws Exception {
            judgeSourceProperties.setUseTestCases(true);
            when(testCaseMapper.findActiveCasesForJudging(100L)).thenReturn(null);

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("flag=false + empty problem_examples → returns null")
        void flagFalseEmptyProblemExamples_returnsNull() throws Exception {
            judgeSourceProperties.setUseTestCases(false);
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L)).thenReturn(List.of());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNull();
            verify(codeExecutionService, never()).execute(any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("flag=true → never consults problem_examples (single-source guarantee)")
        void flagTrueDoesNotConsultProblemExamples() throws Exception {
            judgeSourceProperties.setUseTestCases(true);
            TestCase sample = tc("tc-1", true, false, 1);
            when(testCaseMapper.findActiveCasesForJudging(100L))
                    .thenReturn(List.of(sample));
            when(codeExecutionService.execute(any(), eq(100L), eq("user-1")))
                    .thenReturn(RunResultDTO.builder()
                            .cases(List.of(cr("tc-1", "Accepted", "10", "1.5")))
                            .passedCases(1).totalCases(1).errorMessage(null)
                            .build());

            JudgeExecutionResult result = pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1");

            assertThat(result).isNotNull();
            assertThat(result.verdict()).isEqualTo("Accepted");
            // Even if problem_examples has data, the test_cases path must
            // not consult it (no silent fallback).
            verify(problemExampleMapper, never()).findByProblemIdOrderByOrder(anyLong());
        }
    }

    // === execute — exception propagation ===

    @Nested
    @DisplayName("execute (exception propagation)")
    class ExceptionPropagation {

        @Test
        @DisplayName("sandbox exception propagates (caller decides System Error)")
        void sandboxException_propagates() {
            judgeSourceProperties.setUseTestCases(true);
            when(testCaseMapper.findActiveCasesForJudging(100L))
                    .thenReturn(List.of(tc("tc-1", true, false, 1)));
            when(codeExecutionService.execute(any(), eq(100L), eq("user-1")))
                    .thenThrow(new RuntimeException("sandbox unreachable"));

            assertThatThrownBy(() -> pipeline.execute(
                    "java", "class Solution {}", 100L, "user-1", "sub-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("sandbox unreachable");
        }
    }

    // === determineVerdict ===

    @Nested
    @DisplayName("determineVerdict (case-level reduction)")
    class DetermineVerdict {

        @Test
        @DisplayName("returns Runtime Error when any case has RE (highest priority)")
        void runtimeError_hasHighestPriority() {
            var cases = List.of(
                    cr(null, "Accepted", "50ms", "4.0MB"),
                    cr(null, "Runtime Error", "100ms", "8.0MB"),
                    cr(null, "Accepted", "30ms", "3.0MB")
            );

            String verdict = pipeline.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Runtime Error");
        }

        @Test
        @DisplayName("returns Accepted when all cases pass")
        void allAccepted_returnsAccepted() {
            var cases = List.of(
                    cr(null, "Accepted", "50ms", "4.0MB"),
                    cr(null, "Accepted", "30ms", "3.0MB")
            );

            String verdict = pipeline.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("returns Wrong Answer when any case fails with WA")
        void wrongAnswer_whenPresent() {
            var cases = List.of(
                    cr(null, "Accepted", "50ms", "4.0MB"),
                    cr(null, "Wrong Answer", "30ms", "3.0MB")
            );

            String verdict = pipeline.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Wrong Answer");
        }

        @Test
        @DisplayName("returns Time Limit Exceeded when any case times out")
        void timeLimitExceeded_whenPresent() {
            var cases = List.of(
                    cr(null, "Accepted", "50ms", "4.0MB"),
                    cr(null, "Time Limit Exceeded", "2000ms", "4.0MB")
            );

            String verdict = pipeline.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Time Limit Exceeded");
        }

        @Test
        @DisplayName("priority order: RE > MLE > TLE > WA > PE > Accepted")
        void fullPriorityOrder() {
            var cases = List.of(
                    cr(null, "Accepted", "10ms", "1.0MB"),
                    cr(null, "Presentation Error", "10ms", "1.0MB"),
                    cr(null, "Wrong Answer", "10ms", "1.0MB"),
                    cr(null, "Time Limit Exceeded", "2000ms", "1.0MB"),
                    cr(null, "Memory Limit Exceeded", "10ms", "256.0MB"),
                    cr(null, "Runtime Error", "10ms", "1.0MB")
            );

            assertThat(pipeline.determineVerdict(cases)).isEqualTo("Runtime Error");
        }

        @Test
        @DisplayName("empty case list → System Error (reducer convention)")
        void emptyCases_returnsSystemError() {
            assertThat(pipeline.determineVerdict(List.of())).isEqualTo("System Error");
        }
    }

    // === Helper methods ===

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

    private RunResultDTO.RunCaseResult cr(String testCaseId, String status, String runtime, String memory) {
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
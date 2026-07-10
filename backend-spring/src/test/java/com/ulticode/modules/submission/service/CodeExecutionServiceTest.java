package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.DockerSandboxConfig;
import com.ulticode.modules.submission.port.ProblemFactsPort;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.BatchRunResult;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M2a (ADR-002) version — the {@link SandboxService} collaborator is
 * replaced by the Hexagonal {@link SandboxExecutor} port. All five
 * pre-M2a cases are preserved with mock setups that target the new
 * port signature ({@code run(SandboxJob, TestCase)} /
 * {@code runBatch(SandboxJob, List<TestCase>)}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeExecutionService (M2a, ADR-002)")
class CodeExecutionServiceTest {

    @Mock
    private SandboxExecutor sandboxExecutor;

    @Mock
    private CodeExecutionHelper helper;

    @Mock
    private DockerSandboxConfig sandboxConfig;

    @Mock
    private ProblemFactsPort problemFacts;

    @Spy
    private VerdictResolver verdictResolver = new VerdictResolver();

    private CodeExecutionService codeExecutionService;

    @BeforeEach
    void setUp() {
        // M2a-round-2 fix (codex review F2): the facade now reads
        // per-run defaults from DockerSandboxConfig instead of
        // hard-coding 2s / 256 MiB. Mock the config to 10s / 256m
        // so the existing cases exercise the same effective
        // values the controller used to pass.
        // lenient() because three early-return cases (unsupported
        // language / empty / null testCases) never reach the
        // deriveDefault* helpers, and Mockito strict-stubbing
        // would otherwise flag the unused stubs.
        lenient().when(sandboxConfig.timeout()).thenReturn(10);
        lenient().when(sandboxConfig.memory()).thenReturn("256m");
        // ADR-002 §8 (P2-1): ProblemFactsPort is a plain mock — findLimits
        // returns null by default, so resolveTimeoutSeconds/Mb fall back to
        // the global default (matches pre-P2-1 behaviour). No explicit stub
        // needed (an explicit one trips UnnecessaryStubbing for the cases
        // that pass problemId=null).
        codeExecutionService = new CodeExecutionService(
                sandboxExecutor, helper, verdictResolver, problemFacts,
                new com.ulticode.common.uuid.FixedUuidGenerator(), sandboxConfig);
    }

    private RunSubmissionDTO.RunTestCase createTestCase(String id, String output) {
        RunSubmissionDTO.RunTestCase tc = new RunSubmissionDTO.RunTestCase();
        tc.setId(id);
        tc.setOutput(output);
        return tc;
    }

    private RunSubmissionDTO createRequest(String language, String code, List<RunSubmissionDTO.RunTestCase> testCases) {
        RunSubmissionDTO request = new RunSubmissionDTO();
        request.setLanguage(language);
        request.setCode(code);
        request.setTestCases(testCases);
        return request;
    }

    private RunCaseResult accepted() {
        return RunCaseResult.accepted(10L, 1L * 1024 * 1024);
    }

    private RunCaseResult wrongAnswer() {
        return RunCaseResult.rejected(SubmissionStatus.WRONG_ANSWER, "mismatch", 8L, 1L * 1024 * 1024);
    }

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("unsupported language throws SUBMISSION_LANGUAGE_UNSUPPORTED")
        void execute_unsupportedLanguage_throwsException() {
            RunSubmissionDTO request = createRequest("rust", "fn main() {}", List.of(createTestCase("tc-1", "expected")));

            assertThatThrownBy(() -> codeExecutionService.execute(request, 1L, "user-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED));
        }

        @Test
        @DisplayName("empty test cases delegates to helper.emptyResult and never touches the sandbox")
        void execute_emptyTestCases_returnsEmptyResult() {
            RunSubmissionDTO request = createRequest("python", "print('hello')", List.of());
            RunResultDTO emptyResult = RunResultDTO.builder()
                    .verdict("Accepted").passedCases(0).totalCases(0).cases(List.of()).build();
            when(helper.emptyResult(1L, "user-1")).thenReturn(emptyResult);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result.getVerdict()).isEqualTo("Accepted");
            assertThat(result.getTotalCases()).isEqualTo(0);
            verify(sandboxExecutor, never()).run(any(), any());
            verify(sandboxExecutor, never()).runBatch(any(), anyList());
        }

        @Test
        @DisplayName("null test cases delegates to helper.emptyResult")
        void execute_nullTestCases_returnsEmptyResult() {
            RunSubmissionDTO request = new RunSubmissionDTO();
            request.setLanguage("python");
            request.setCode("print('hello')");
            request.setTestCases(null);
            RunResultDTO emptyResult = RunResultDTO.builder()
                    .verdict("Accepted").passedCases(0).totalCases(0).cases(List.of()).build();
            when(helper.emptyResult(1L, "user-1")).thenReturn(emptyResult);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result.getVerdict()).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("single test case delegates to sandboxExecutor.run and verdict is Accepted")
        void execute_singleTestCase_delegatesToSandbox() {
            RunSubmissionDTO.RunTestCase tc = createTestCase("tc-1", "42");
            RunSubmissionDTO request = createRequest("python", "def solution(): pass", List.of(tc));
            when(sandboxExecutor.run(any(SandboxJob.class), any(TestCase.class))).thenReturn(accepted());
            // ADR-002 §8: execute() now aggregates via dto.runtimeMs (set by
            // toDtoCaseResult from port.elapsedMs), so parseRuntimeMs is no
            // longer on the hot path — lenient so the stub stays tolerant.
            lenient().when(helper.parseRuntimeMs(anyString())).thenReturn(10L);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result.getVerdict()).isEqualTo("Accepted");
            assertThat(result.getPassedCases()).isEqualTo(1);
            assertThat(result.getTotalCases()).isEqualTo(1);
            verify(sandboxExecutor).run(any(SandboxJob.class), any(TestCase.class));
            verify(sandboxExecutor, never()).runBatch(any(), anyList());
        }

        @Test
        @DisplayName("multiple test cases delegates to sandboxExecutor.runBatch and verdict follows VerdictResolver")
        void execute_multipleTestCases_delegatesToSandbox() {
            RunSubmissionDTO.RunTestCase tc1 = createTestCase("tc-1", "42");
            RunSubmissionDTO.RunTestCase tc2 = createTestCase("tc-2", "10");
            RunSubmissionDTO request = createRequest("python", "def solution(): pass", List.of(tc1, tc2));
            when(sandboxExecutor.runBatch(any(SandboxJob.class), anyList()))
                    .thenReturn(new BatchRunResult(List.of(accepted(), wrongAnswer())));
            // ADR-002 §8: execute() now aggregates via dto.runtimeMs (set by
            // toDtoCaseResult from port.elapsedMs), so parseRuntimeMs is no
            // longer on the hot path — lenient so the stub stays tolerant.
            lenient().when(helper.parseRuntimeMs(anyString())).thenReturn(10L);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            // Per VerdictResolver, severity(WRONG_ANSWER=2) > severity(ACCEPTED=0),
            // so the per-case reduce must return WRONG_ANSWER.
            assertThat(result.getVerdict()).isEqualTo("Wrong Answer");
            assertThat(result.getPassedCases()).isEqualTo(1);
            assertThat(result.getTotalCases()).isEqualTo(2);
            verify(sandboxExecutor).runBatch(any(SandboxJob.class), anyList());
            verify(sandboxExecutor, never()).run(any(), any());
        }

        @Test
        @DisplayName("toDtoCaseResult forwards inputs / output / expectedOutput to the wire DTO (R-T1 regression)")
        void execute_forwardsInputsOutputAndExpectedToWire() {
            // Mirrors the merge-k-sorted-lists /run scenario from the UI:
            // sandbox returns a port-level result that has both the
            // harness stdout and the user-supplied inputs populated.
            // Pre-fix, toDtoCaseResult only forwarded .detail and the
            // console's TestResultsView rendered every case as
            // "此用例未返回可展示的输入输出详情".
            List<TestCase.Input> sandboxInputs = List.of(
                    new TestCase.Input("in-1", "lists", "lists", "[[1,4,5],[1,3,4],[2,6]]", null));
            RunCaseResult portResult = RunCaseResult.acceptedWithOutput(
                    2L, 6L * 1024 * 1024,
                    "[1,1,2,3,4,4,5,6]",
                    "[1,1,2,3,4,4,5,6]",
                    sandboxInputs);

            RunSubmissionDTO.RunInput in = new RunSubmissionDTO.RunInput();
            in.setId("in-1");
            in.setLabel("lists");
            in.setName("lists");
            in.setValue("[[1,4,5],[1,3,4],[2,6]]");
            RunSubmissionDTO.RunTestCase tc = new RunSubmissionDTO.RunTestCase();
            tc.setId("pe-007-1");
            tc.setLabel("Case 1");
            tc.setOutput("[1,1,2,3,4,4,5,6]");
            tc.setInputs(List.of(in));
            RunSubmissionDTO request = createRequest("java", "class Solution {}", List.of(tc));
            when(sandboxExecutor.run(any(SandboxJob.class), any(TestCase.class))).thenReturn(portResult);
            // ADR-002 §8: aggregation now uses dto.runtimeMs (set from
            // port.elapsedMs=2), so parseRuntimeMs is off the hot path.
            lenient().when(helper.parseRuntimeMs(anyString())).thenReturn(2L);

            RunResultDTO result = codeExecutionService.execute(request, 7L, "user-1");

            assertThat(result.getCases()).hasSize(1);
            RunResultDTO.RunCaseResult dto = result.getCases().get(0);
            assertThat(dto.getOutput()).isEqualTo("[1,1,2,3,4,4,5,6]");
            assertThat(dto.getExpectedOutput()).isEqualTo("[1,1,2,3,4,4,5,6]");
            assertThat(dto.getInputs()).hasSize(1);
            RunResultDTO.RunCaseResult.InputParam dtoInput = dto.getInputs().get(0);
            assertThat(dtoInput.getId()).isEqualTo("in-1");
            assertThat(dtoInput.getLabel()).isEqualTo("lists");
            assertThat(dtoInput.getName()).isEqualTo("lists");
            assertThat(dtoInput.getValue()).isEqualTo("[[1,4,5],[1,3,4],[2,6]]");
        }
    }
}

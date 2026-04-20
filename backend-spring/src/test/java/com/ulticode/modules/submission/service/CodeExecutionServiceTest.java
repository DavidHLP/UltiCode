package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeExecutionService")
class CodeExecutionServiceTest {

    @Mock
    private SandboxService sandboxService;

    @Mock
    private CodeExecutionHelper helper;

    private CodeExecutionService codeExecutionService;

    @BeforeEach
    void setUp() {
        codeExecutionService = new CodeExecutionService(sandboxService, helper);
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
        @DisplayName("empty test cases delegates to helper.emptyResult")
        void execute_emptyTestCases_returnsEmptyResult() {
            RunSubmissionDTO request = createRequest("python", "print('hello')", List.of());
            RunResultDTO emptyResult = RunResultDTO.builder()
                    .verdict("Accepted").passedCases(0).totalCases(0).cases(List.of()).build();
            when(helper.emptyResult(1L, "user-1")).thenReturn(emptyResult);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result.getVerdict()).isEqualTo("Accepted");
            assertThat(result.getTotalCases()).isEqualTo(0);
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
        @DisplayName("single test case delegates to sandboxService.executeInSandbox")
        void execute_singleTestCase_delegatesToSandbox() {
            RunSubmissionDTO.RunTestCase tc = createTestCase("tc-1", "42");
            RunSubmissionDTO request = createRequest("python", "def solution(): pass", List.of(tc));
            RunResultDTO.RunCaseResult caseResult = RunResultDTO.RunCaseResult.builder()
                    .status("Accepted").runtime("10ms").memory("1.0MB").build();
            when(sandboxService.executeInSandbox(eq("python"), eq("def solution(): pass"), eq(tc), anyString(), eq("user-1")))
                    .thenReturn(caseResult);
            when(helper.parseRuntimeMs("10ms")).thenReturn(10L);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result.getVerdict()).isEqualTo("Accepted");
            assertThat(result.getPassedCases()).isEqualTo(1);
            assertThat(result.getTotalCases()).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple test cases delegates to sandboxService.executeBatch")
        void execute_multipleTestCases_delegatesToSandbox() {
            RunSubmissionDTO.RunTestCase tc1 = createTestCase("tc-1", "42");
            RunSubmissionDTO.RunTestCase tc2 = createTestCase("tc-2", "10");
            RunSubmissionDTO request = createRequest("python", "def solution(): pass", List.of(tc1, tc2));
            RunResultDTO.RunCaseResult r1 = RunResultDTO.RunCaseResult.builder().status("Accepted").runtime("10ms").memory("1.0MB").build();
            RunResultDTO.RunCaseResult r2 = RunResultDTO.RunCaseResult.builder().status("Wrong Answer").runtime("8ms").memory("1.0MB").build();
            when(sandboxService.executeBatch(eq("python"), eq("def solution(): pass"), eq(List.of(tc1, tc2)), anyString(), eq("user-1")))
                    .thenReturn(List.of(r1, r2));
            when(helper.parseRuntimeMs("10ms")).thenReturn(10L);
            when(helper.parseRuntimeMs("8ms")).thenReturn(8L);

            RunResultDTO result = codeExecutionService.execute(request, 1L, "user-1");

            assertThat(result.getVerdict()).isEqualTo("Wrong Answer");
            assertThat(result.getPassedCases()).isEqualTo(1);
            assertThat(result.getTotalCases()).isEqualTo(2);
        }
    }
}

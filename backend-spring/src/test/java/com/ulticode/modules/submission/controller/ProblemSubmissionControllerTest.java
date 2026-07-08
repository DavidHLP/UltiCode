package com.ulticode.modules.submission.controller;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.SubmissionService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProblemSubmissionController")
class ProblemSubmissionControllerTest {

    @Mock
    private SubmissionService submissionService;

    @Mock
    private CodeExecutionService codeExecutionService;

    @Mock
    private CurrentUserProvider currentUserProvider;
    private Validator validator;

    private ProblemSubmissionController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        controller = new ProblemSubmissionController(submissionService, codeExecutionService, validator, currentUserProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("runCode allows anonymous sample execution")
    void runCode_anonymousUser_delegatesWithNullUserId() {
        RunSubmissionDTO request = new RunSubmissionDTO();
        request.setLanguage("python");
        request.setCode("print('ok')");
        request.setTestCases(List.of(new RunSubmissionDTO.RunTestCase()));
        RunResultDTO expected = RunResultDTO.builder()
                .problemId(1L)
                .userId(null)
                .verdict("Accepted")
                .cases(List.of())
                .passedCases(0)
                .totalCases(0)
                .build();
        when(codeExecutionService.execute(eq(request), eq(1L), eq(null))).thenReturn(expected);

        RunResultDTO result = controller.runCode(1L, request).getData();

        assertThat(result).isSameAs(expected);
        verify(codeExecutionService).execute(request, 1L, null);
    }

    @Test
    @DisplayName("submitForProblem still requires authentication")
    void submitForProblem_anonymousUser_throwsUnauthorized() {
        CreateSubmissionDTO request = new CreateSubmissionDTO();
        request.setLanguage("python");
        request.setCode("print('ok')");

        assertThatThrownBy(() -> controller.submitForProblem(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}

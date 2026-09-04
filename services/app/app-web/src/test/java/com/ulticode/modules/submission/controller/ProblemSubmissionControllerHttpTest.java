package com.ulticode.modules.submission.controller;

import com.ulticode.modules.submission.controller.RunResultDTO;
import com.ulticode.modules.submission.port.InteractiveCodeRunner;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.app.error.ProblemWebExceptionHandler;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProblemSubmissionControllerHttpTest {

    private final InteractiveCodeRunner codeExecutionPort = mock(InteractiveCodeRunner.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProblemSubmissionController controller = new ProblemSubmissionController(
                mock(SubmissionUserQueryPort.class),
                mock(SubmissionIntakePort.class),
                codeExecutionPort,
                mock(Validator.class),
                mock(CurrentUserProvider.class));
        mockMvc = standaloneSetup(controller)
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()))
                .setControllerAdvice(new ProblemWebExceptionHandler())
                .build();
    }

    @Test
    void runReturnsJudgeResult() throws Exception {
        when(codeExecutionPort.run(any(), eq(42L), isNull())).thenReturn(
                RunResultDTO.builder()
                        .id("run-1")
                        .problemId(42L)
                        .verdict("Accepted")
                        .cases(List.of())
                        .build());

        mockMvc.perform(post("/problems/42/submissions/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("run-1"))
                .andExpect(jsonPath("$.data.verdict").value("Accepted"));
    }

    @Test
    void judgeUnavailableReturnsTyped503() throws Exception {
        when(codeExecutionPort.run(any(), eq(42L), isNull())).thenThrow(
                new BusinessException(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE));

        mockMvc.perform(post("/problems/42/submissions/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(30022))
                .andExpect(jsonPath("$.message").value("Code execution is unavailable"));
    }
    @Test
    void blankCaseIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/problems/42/submissions/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson().replace("\"case-1\"", "\"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nullNestedInputReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/problems/42/submissions/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson().replace("\"inputs\": []", "\"inputs\": [null]")))
                .andExpect(status().isBadRequest());
    }


    private static String requestJson() {
        return """
                {
                  "language": "python",
                  "code": "print('ok')",
                  "testCases": [{"id": "case-1", "inputs": [], "output": "ok"}]
                }
                """;
    }
}

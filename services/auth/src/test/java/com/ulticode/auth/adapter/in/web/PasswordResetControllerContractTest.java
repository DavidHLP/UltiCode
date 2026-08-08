package com.ulticode.auth.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.dto.ForgotPasswordDTO;
import com.ulticode.auth.dto.ResetPasswordDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.service.PasswordResetWorkflow;
import com.ulticode.common.error.BaseErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetControllerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PasswordResetWorkflow passwordResetWorkflow;
    private PasswordResetController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        passwordResetWorkflow = mock(PasswordResetWorkflow.class);
        controller = new PasswordResetController(passwordResetWorkflow);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.ulticode.auth.error.AuthWebExceptionHandler())
                .build();
    }

    @Test
    void forgotPasswordPreservesGenericSuccessEnvelopeAndDelegates() throws Exception {
        ForgotPasswordDTO request = new ForgotPasswordDTO();
        request.setEmail("unknown@example.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(passwordResetWorkflow).forgotPassword("unknown@example.com");
    }

    @Test
    void resetPasswordPreservesSuccessEnvelopeAndDelegates() throws Exception {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setToken("raw-token-123");
        request.setNewPassword("NewPass123");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(passwordResetWorkflow).resetPassword("raw-token-123", "NewPass123");
    }

    @Test
    void resetPasswordKeepsShortPasswordValidationAtHttpBoundary() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setToken("raw-token-123");
        request.setNewPassword("short");

        assertThatThrownBy(() -> controller.resetPassword(request))
                .isInstanceOf(AuthBusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                                ((AuthBusinessException) ex).getErrorCode())
                        .isEqualTo(BaseErrorCode.VALIDATION_FAILED));

        verifyNoInteractions(passwordResetWorkflow);
    }
}

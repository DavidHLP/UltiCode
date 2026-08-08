package com.ulticode.auth.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.dto.ChangePasswordDTO;
import com.ulticode.auth.error.AuthWebExceptionHandler;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserCredentialControllerTest {

    private AccountManagementService accountManagementService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        accountManagementService = mock(AccountManagementService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        objectMapper = new ObjectMapper();

        UserCredentialController controller = new UserCredentialController(
                accountManagementService, currentUserProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthWebExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("changePassword returns 200 success when authentication and command succeed")
    void changePasswordSuccess() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-100");
        AccountMutationDTO dto = new AccountMutationDTO(
                "user-100", "alice", "alice@example.com", "USER", true, false, 1L, false);
        when(accountManagementService.changePassword(any(ChangePasswordCommand.class)))
                .thenReturn(RpcResult.success(dto, "t-123"));

        ChangePasswordDTO body = new ChangePasswordDTO("oldPass123", "newPass123", "newPass123");

        mockMvc.perform(patch("/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("changePassword returns 400 when confirm password does not match new password")
    void changePasswordMismatch() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-100");

        ChangePasswordDTO body = new ChangePasswordDTO("oldPass123", "newPass123", "differentPass123");

        mockMvc.perform(patch("/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("changePassword returns 400 when current password is incorrect on Auth provider")
    void changePasswordIncorrectCurrentPassword() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-100");
        when(accountManagementService.changePassword(any(ChangePasswordCommand.class)))
                .thenReturn(RpcResult.failure(AuthErrorCode.PASSWORD_MISMATCH, "t-123"));

        ChangePasswordDTO body = new ChangePasswordDTO("wrongOldPass", "newPass123", "newPass123");

        mockMvc.perform(patch("/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("changePassword returns 401 when user is not authenticated")
    void changePasswordUnauthenticated() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        ChangePasswordDTO body = new ChangePasswordDTO("oldPass123", "newPass123", "newPass123");

        mockMvc.perform(patch("/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}

package com.ulticode.modules.admin.controller;

import com.ulticode.admin.error.AdminWebExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.ChangePasswordDTO;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserManagementService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {AdminAccountController.class, AdminWebExceptionHandler.class})
@DisplayName("AdminAccountController")
class AdminAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminUserProjection adminUserProjection;
    @MockBean
    private UserManagementService userManagementService;
    @MockBean
    private AccountManagementService accountManagementService;
    @MockBean
    private CurrentUserProvider currentUserProvider;


    private static final String VALID_BODY =
            "{\"currentPassword\":\"current-password\","
                    + "\"newPassword\":\"new-password\","
                    + "\"confirmPassword\":\"new-password\"}";

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-123");
    }

    @Nested
    @DisplayName("POST /admin/account/change-password")
    class ChangePassword {

        @Test
        @DisplayName("delegates to AccountManagementService on valid request")
        void delegatesToAccountManagementService() throws Exception {
            AccountMutationDTO dto = new AccountMutationDTO(
                    "admin-123", "admin", "admin@example.com", "ADMIN", true, false, 0L, false);
            when(accountManagementService.changePassword(any(ChangePasswordCommand.class)))
                    .thenReturn(RpcResult.success(dto, "t-123"));

            mockMvc.perform(post("/admin/account/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            ArgumentCaptor<ChangePasswordCommand> captor = ArgumentCaptor.forClass(ChangePasswordCommand.class);
            verify(accountManagementService).changePassword(captor.capture());
            ChangePasswordCommand command = captor.getValue();
            assertThat(command.trace()).isNotNull();
            assertThat(command.trace().traceId()).isNotBlank();
            assertThat(command.actor().actorId()).isEqualTo("admin-123");
        }


        @Test
        @DisplayName("rejects a null Auth response instead of reporting success")
        void nullAuthResponse() throws Exception {
            when(accountManagementService.changePassword(any(ChangePasswordCommand.class)))
                    .thenReturn(null);

            mockMvc.perform(post("/admin/account/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isInternalServerError());
        }
        @Test
        @DisplayName("rejects a missing confirm password (400)")
        void missingConfirmPassword() throws Exception {
            mockMvc.perform(post("/admin/account/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"current-password\","
                                    + "\"newPassword\":\"new-password\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rejects a missing new password (400)")
        void shortNewPassword() throws Exception {
            mockMvc.perform(post("/admin/account/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"current-password\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}

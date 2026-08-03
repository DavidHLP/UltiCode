package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.UlticodeBackendApplication;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.ChangePasswordDTO;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AdminAccountController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@ContextConfiguration(classes = UlticodeBackendApplication.class)
@AutoConfigureMockMvc(addFilters = false)
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

    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtProperties jwtProperties;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean private CorsProperties corsProperties;
    @MockBean private StringRedisTemplate stringRedisTemplate;

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

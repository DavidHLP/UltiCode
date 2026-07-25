package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.port.UserWritePort;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest for AdminAccountController.
 *
 * <p>Mirrors {@link AdminSettingsControllerTest}: {@code addFilters=false}
 * bypasses security; auth is exercised in {@code PrivilegedControllerAuthorizationTest}.
 * The password-change regression proves the endpoint delegates to the deep
 * {@link UserWritePort} seam instead of returning the historical false-success
 * response that left the administrator password unchanged.
 */
@WebMvcTest(
        value = AdminAccountController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminAccountController")
class AdminAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserReadProjection userReadProjection;
    @MockBean
    private UserWritePort userWritePort;
    @MockBean
    private CurrentUserProvider currentUserProvider;

    // SecurityConfig dependencies (excluded by @WebMvcTest)
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

    @Nested
    @DisplayName("POST /admin/account/change-password")
    class ChangePassword {

        @Test
        @DisplayName("delegates to the deep UserWritePort seam (no false success)")
        void delegatesToUserWritePort() throws Exception {
            mockMvc.perform(post("/admin/account/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(userWritePort).changePassword(any(ChangePasswordDTO.class));
        }

        @Test
        @DisplayName("rejects a missing confirm password (validation boundary)")
        void missingConfirmPassword() throws Exception {
            mockMvc.perform(post("/admin/account/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"current-password\","
                                    + "\"newPassword\":\"new-password\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.confirmPassword").exists());
        }

        @Test
        @DisplayName("rejects a too-short new password (policy boundary)")
        void shortNewPassword() throws Exception {
            mockMvc.perform(post("/admin/account/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"current-password\","
                                    + "\"newPassword\":\"short\","
                                    + "\"confirmPassword\":\"short\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.newPassword").exists());
        }
    }
}

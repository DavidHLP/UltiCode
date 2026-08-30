package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthWebExceptionHandler;
import com.ulticode.auth.service.AuthenticationWorkflow;
import com.ulticode.auth.service.CurrentSessionQuery;
import com.ulticode.auth.session.SessionCookieAdapter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerCurrentSessionContractTest {

    private static final Principal USER_PRINCIPAL = () -> "user-1";

    private AuthenticationWorkflow authenticationWorkflow;
    private CurrentSessionQuery currentSessionQuery;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticationWorkflow = mock(AuthenticationWorkflow.class);
        currentSessionQuery = mock(CurrentSessionQuery.class);
        AuthController controller = new AuthController(
                authenticationWorkflow, new SessionCookieAdapter(), currentSessionQuery);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthWebExceptionHandler())
                .build();
    }

    @Test
    void currentUserMapsSafeProjectionAndCsrfTokenToExistingHttpShape() throws Exception {
        when(currentSessionQuery.currentUser("user-1")).thenReturn(currentUser());

        mockMvc.perform(get("/auth/me").principal(USER_PRINCIPAL)
                        .cookie(new Cookie("csrf_token", "csrf-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.id").value("user-1"))
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.user.name").value("alice"))
                .andExpect(jsonPath("$.data.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.is_active").value(true))
                .andExpect(jsonPath("$.data.user.is_banned").value(false))
                .andExpect(jsonPath("$.data.csrfToken").value("csrf-1"));

        verify(currentSessionQuery).currentUser("user-1");
    }

    @Test
    void permissionsMapsQueryResultToExistingHttpEnvelope() throws Exception {
        when(currentSessionQuery.permissions("user-1"))
                .thenReturn(List.of("READ:PROBLEM", "SUBMIT:PROBLEM"));

        mockMvc.perform(get("/auth/permissions").principal(USER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0]").value("READ:PROBLEM"))
                .andExpect(jsonPath("$.data[1]").value("SUBMIT:PROBLEM"));

        verify(currentSessionQuery).permissions("user-1");
    }

    @Test
    void currentUserRejectsMissingPrincipal() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_TOKEN_EXPIRED.code()));

        verifyNoInteractions(currentSessionQuery);
    }

    @Test
    void permissionsRejectsMissingPrincipal() throws Exception {
        mockMvc.perform(get("/auth/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_TOKEN_EXPIRED.code()));

        verifyNoInteractions(currentSessionQuery);
    }

    @Test
    void currentUserMapsMissingAccountToAuthNotFound() throws Exception {
        when(currentSessionQuery.currentUser("missing"))
                .thenThrow(new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND));

        mockMvc.perform(get("/auth/me").principal(() -> "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_USER_NOT_FOUND.code()));

        verify(currentSessionQuery).currentUser("missing");
    }

    private CurrentSessionQuery.CurrentUser currentUser() {
        return new CurrentSessionQuery.CurrentUser(
                "user-1",
                "alice",
                "alice@example.com",
                "USER",
                true,
                false,
                LocalDateTime.of(2026, 8, 6, 12, 0)
        );
    }
}

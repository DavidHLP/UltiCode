package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.dto.AuthUserVO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.service.OAuthLoginWorkflow;
import com.ulticode.auth.session.CookieMutation;
import com.ulticode.auth.session.SessionCookieAdapter;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.auth.error.AuthErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OAuthControllerContractTest {

    private OAuthLoginWorkflow oauthLoginWorkflow;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        oauthLoginWorkflow = mock(OAuthLoginWorkflow.class);
        OAuthController controller = new OAuthController(oauthLoginWorkflow, new SessionCookieAdapter());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.ulticode.auth.error.AuthWebExceptionHandler())
                .build();
    }

    @Test
    void githubAuthUrlPreservesResultAndStateCookiePolicy() throws Exception {
        when(oauthLoginWorkflow.begin("github"))
                .thenReturn(new OAuthLoginWorkflow.OAuthAuthorization(
                        "https://github.example/authorize?state=state-1",
                        new CookieMutation("oauth_state_github", "state-1", 300, true, true, "Lax", "/auth", null)));

        mockMvc.perform(get("/auth/oauth/github/auth-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.authUrl").value("https://github.example/authorize?state=state-1"))
                .andExpect(cookie().value("oauth_state_github", "state-1"))
                .andExpect(cookie().httpOnly("oauth_state_github", true))
                .andExpect(cookie().secure("oauth_state_github", true))
                .andExpect(cookie().path("oauth_state_github", "/auth"))
                .andExpect(cookie().maxAge("oauth_state_github", 300));

        verify(oauthLoginWorkflow).begin("github");
    }

    @Test
    void googleCallbackPreservesLoginResponseAndClearsStateCookie() throws Exception {
        LoginResponse loginResponse = LoginResponse.builder()
                .csrfToken("csrf-google")
                .user(new AuthUserVO("user-1", "google-user", "Google User", "google@example.com",
                        "USER", true, false, ""))
                .build();
        when(oauthLoginWorkflow.complete("google", "code-1", "state-1", "state-1"))
                .thenReturn(new OAuthLoginWorkflow.OAuthCompletion(
                        loginResponse,
                        List.of(
                                new CookieMutation("oauth_state_google", "", 0, true, true, "Lax", "/auth", null),
                                new CookieMutation("access_token", "access", 900, true, true, "Lax", "/", null),
                                new CookieMutation("refresh_token", "refresh", 604800, true, true, "Lax", "/", null),
                                new CookieMutation("csrf_token", "csrf-google", 900, false, true, "Lax", "/", null))));

        mockMvc.perform(get("/auth/oauth/GoOgLe/callback")
                        .param("code", "code-1")
                        .param("state", "state-1")
                        .cookie(new Cookie("oauth_state_google", "state-1"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.csrfToken").value("csrf-google"))
                .andExpect(cookie().value("oauth_state_google", ""))
                .andExpect(cookie().httpOnly("oauth_state_google", true))
                .andExpect(cookie().secure("oauth_state_google", true))
                .andExpect(cookie().path("oauth_state_google", "/auth"))
                .andExpect(cookie().maxAge("oauth_state_google", 0))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("csrf_token", false));

        verify(oauthLoginWorkflow).complete("google", "code-1", "state-1", "state-1");
    }

    @Test
    void unsupportedProviderUsesExistingBadRequestEnvelope() throws Exception {
        when(oauthLoginWorkflow.begin("gitlab"))
                .thenThrow(new AuthBusinessException(BaseErrorCode.BAD_REQUEST, "Unsupported OAuth provider: gitlab"));

        mockMvc.perform(get("/auth/oauth/gitlab/auth-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.message").value("Unsupported OAuth provider: gitlab"));
    }

    @Test
    void failedCallbackAppliesStateCleanupBeforeExistingErrorHandler() throws Exception {
        AuthBusinessException original = new AuthBusinessException(
                AuthErrorCode.AUTH_INVALID_CREDENTIALS, "OAuth state invalid");
        when(oauthLoginWorkflow.complete(eq("github"), eq("code-1"), eq("bad-state"), eq(null)))
                .thenThrow(new OAuthLoginWorkflow.OAuthCallbackFailure(
                        original,
                        new CookieMutation("oauth_state_github", "", 0, true, true, "Lax", "/auth", null)));

        mockMvc.perform(get("/auth/oauth/github/callback")
                        .param("code", "code-1")
                        .param("state", "bad-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTH_INVALID_CREDENTIALS.code()))
                .andExpect(cookie().value("oauth_state_github", ""))
                .andExpect(cookie().httpOnly("oauth_state_github", true))
                .andExpect(cookie().secure("oauth_state_github", true))
                .andExpect(cookie().path("oauth_state_github", "/auth"))
                .andExpect(cookie().maxAge("oauth_state_github", 0));
    }
}

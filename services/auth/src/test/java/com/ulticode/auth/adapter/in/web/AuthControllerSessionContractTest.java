package com.ulticode.auth.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.dto.AuthUserVO;
import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.error.AuthWebExceptionHandler;
import com.ulticode.auth.service.AuthenticationWorkflow;
import com.ulticode.auth.service.CurrentSessionQuery;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.CookieMutation;
import com.ulticode.auth.session.SessionCookieAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerSessionContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthenticationWorkflow authenticationWorkflow;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticationWorkflow = mock(AuthenticationWorkflow.class);
        AuthController controller = new AuthController(
                authenticationWorkflow, new SessionCookieAdapter(), mock(CurrentSessionQuery.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthWebExceptionHandler())
                .build();
    }

    @Test
    void loginPreservesResponseBodyAndCookiePolicy() throws Exception {
        LoginDTO request = new LoginDTO();
        request.setUsername("alice");
        request.setPassword("Secret123");

        LoginResponse body = LoginResponse.builder()
                .csrfToken("csrf")
                .user(new AuthUserVO("user-1", "alice", "alice", "alice@example.com", "USER", true, false, ""))
                .build();
        AuthSession session = new AuthSession(
                body,
                List.of(
                        CookieMutation.set("access_token", "access", 900, true),
                        CookieMutation.set("refresh_token", "refresh", 604800, true),
                        CookieMutation.set("csrf_token", "csrf", 900, false)
                )
        );
        when(authenticationWorkflow.login("alice", "Secret123")).thenReturn(session);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.csrfToken").value("csrf"))
                .andExpect(cookie().value("access_token", "access"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().path("access_token", "/"))
                .andExpect(cookie().maxAge("access_token", 900))
                .andExpect(cookie().value("refresh_token", "refresh"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().maxAge("refresh_token", 604800))
                .andExpect(cookie().value("csrf_token", "csrf"))
                .andExpect(cookie().httpOnly("csrf_token", false))
                .andExpect(cookie().maxAge("csrf_token", 900));
    }
}

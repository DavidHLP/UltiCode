package com.ulticode.auth.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.dto.RegisterDTO;
import com.ulticode.auth.error.AuthWebExceptionHandler;
import com.ulticode.auth.service.AuthenticationWorkflow;
import com.ulticode.auth.service.CurrentSessionQuery;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.SessionCookieAdapter;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.aspect.RateLimitAspect;
import com.ulticode.websecurity.ratelimiter.InMemoryRateLimiter;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proxied AuthController rate-limit regression. Proves the {@link com.ulticode.websecurity.annotation.RateLimit}
 * annotation on login/register/refresh enforces the exact legacy thresholds
 * (10/60, 5/60, 20/60) by driving the real AuthController through MockMvc
 * with the real {@link RateLimitAspect} backed by an {@link InMemoryRateLimiter}
 * (no Redis). All endpoints use {@code {ip}} buckets; the
 * {@code CurrentUserProvider} returns {@code null} so every call is bucketed
 * per client IP.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuthControllerRateLimitTest.TestConfig.class)
@WebAppConfiguration
class AuthControllerRateLimitTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private AuthenticationWorkflow authenticationWorkflow;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        reset(authenticationWorkflow);
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("login enforces 10/60: N allowed, N+1 returns 429/code 42900/Retry-After")
    void loginEnforcesThreshold() throws Exception {
        when(authenticationWorkflow.login(any(), any())).thenReturn(successSession());

        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(loginDto())))
                    .andExpect(status().isOk());
        }

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(loginDto())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(header().exists("Retry-After"))
                .andDo(result -> {
                    String retryAfter = result.getResponse().getHeader("Retry-After");
                    assertThat(retryAfter).isNotNull();
                    assertThat(Long.parseLong(retryAfter)).isPositive();
                });

        verify(authenticationWorkflow, times(10)).login(any(), any());
    }

    @Test
    @DisplayName("register enforces 5/60: N+1 returns 429 and does not invoke workflow past threshold")
    void registerEnforcesThresholdAndSkipsWorkflow() throws Exception {
        when(authenticationWorkflow.register(any(), any(), any())).thenReturn(successSession());

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(registerDto())))
                    .andExpect(status().isOk());
        }

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(registerDto())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(header().exists("Retry-After"))
                .andDo(result -> {
                    String retryAfter = result.getResponse().getHeader("Retry-After");
                    assertThat(retryAfter).isNotNull();
                    assertThat(Long.parseLong(retryAfter)).isPositive();
                });

        verify(authenticationWorkflow, times(5)).register(any(), any(), any());
    }

    @Test
    @DisplayName("refresh enforces 20/60: N+1 returns 429/code 42900/Retry-After")
    void refreshEnforcesThreshold() throws Exception {
        when(authenticationWorkflow.refresh(any())).thenReturn(successSession());

        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/auth/refresh"))
                    .andExpect(status().isOk());
        }

        mvc.perform(post("/auth/refresh"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(header().exists("Retry-After"))
                .andDo(result -> {
                    String retryAfter = result.getResponse().getHeader("Retry-After");
                    assertThat(retryAfter).isNotNull();
                    assertThat(Long.parseLong(retryAfter)).isPositive();
                });

        verify(authenticationWorkflow, times(20)).refresh(any());
    }

    @Test
    @DisplayName("rejected login does not invoke AuthenticationWorkflow")
    void rejectedRequestSkipsWorkflow() throws Exception {
        when(authenticationWorkflow.login(any(), any())).thenReturn(successSession());
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(loginDto())))
                    .andExpect(status().isOk());
        }
        verify(authenticationWorkflow, times(10)).login(any(), any());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(loginDto())))
                .andExpect(status().isTooManyRequests());

        verify(authenticationWorkflow, times(10)).login(any(), any());
    }

    private static AuthSession successSession() {
        return new AuthSession(LoginResponse.builder().csrfToken("csrf").build(), List.of());
    }

    private static LoginDTO loginDto() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("Secret123");
        return dto;
    }

    private static RegisterDTO registerDto() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("bob");
        dto.setEmail("bob@example.com");
        dto.setPassword("Secret123");
        return dto;
    }

    @Configuration
    @EnableWebMvc
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        AuthenticationWorkflow authenticationWorkflow() {
            return mock(AuthenticationWorkflow.class);
        }

        @Bean
        AuthController authController(AuthenticationWorkflow authenticationWorkflow) {
            return new AuthController(authenticationWorkflow, new SessionCookieAdapter(), mock(CurrentSessionQuery.class));
        }

        @Bean
        AuthWebExceptionHandler authWebExceptionHandler() {
            return new AuthWebExceptionHandler();
        }

        @Bean
        InMemoryRateLimiter inMemoryRateLimiter() {
            return new InMemoryRateLimiter();
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver();
        }

        @Bean
        CurrentUserProvider currentUserProvider() {
            return new CurrentUserProvider() {
                @Override
                public String getCurrentUserId() {
                    return null;
                }

                @Override
                public String getCurrentUsername() {
                    return null;
                }

                @Override
                public boolean isAuthenticated() {
                    return false;
                }

                @Override
                public boolean hasRole(String role) {
                    return false;
                }

                @Override
                public boolean hasAnyRole(String... roles) {
                    return false;
                }
            };
        }

        @Bean
        RateLimitAspect rateLimitAspect(
                InMemoryRateLimiter rateLimiter,
                ClientIpResolver clientIpResolver,
                CurrentUserProvider currentUserProvider) {
            return new RateLimitAspect(rateLimiter, clientIpResolver, currentUserProvider);
        }
    }
}

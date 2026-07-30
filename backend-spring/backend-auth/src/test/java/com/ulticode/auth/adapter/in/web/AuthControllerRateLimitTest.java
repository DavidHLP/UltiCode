package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.dto.RegisterDTO;
import com.ulticode.auth.error.AuthWebExceptionHandler;
import com.ulticode.auth.service.AuthService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.aspect.RateLimitAspect;
import com.ulticode.websecurity.ratelimiter.InMemoryRateLimiter;
import com.ulticode.websecurity.util.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

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
    private AuthService authService;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        reset(authService);
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("login enforces 10/60: N allowed, N+1 returns 429/code 42900/Retry-After")
    void loginEnforcesThreshold() throws Exception {
        when(authService.login(any(), any())).thenReturn(successResponse());

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

        verify(authService, times(10)).login(any(), any());
    }

    @Test
    @DisplayName("register enforces 5/60: N+1 returns 429 and does not invoke AuthService past threshold")
    void registerEnforcesThresholdAndSkipsService() throws Exception {
        when(authService.register(any(), any())).thenReturn(successResponse());

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

        verify(authService, times(5)).register(any(), any());
    }

    @Test
    @DisplayName("refresh enforces 20/60: N+1 returns 429/code 42900/Retry-After")
    void refreshEnforcesThreshold() throws Exception {
        when(authService.refresh(any(), any())).thenReturn(successResponse());

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

        verify(authService, times(20)).refresh(any(), any());
    }

    @Test
    @DisplayName("rejected login does not invoke AuthService")
    void rejectedRequestSkipsService() throws Exception {
        when(authService.login(any(), any())).thenReturn(successResponse());
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(loginDto())))
                    .andExpect(status().isOk());
        }
        verify(authService, times(10)).login(any(), any());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(loginDto())))
                .andExpect(status().isTooManyRequests());

        verify(authService, times(10)).login(any(), any());
    }

    private static LoginResponse successResponse() {
        return LoginResponse.builder().csrfToken("csrf").build();
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
        AuthService authService() {
            return mock(AuthService.class);
        }

        @Bean
        AuthController authController(AuthService authService) {
            return new AuthController(authService, null, null, null);
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

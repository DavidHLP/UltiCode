package com.ulticode.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.RegisterDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * E2E tests for rate limiting on auth endpoints.
 *
 * <p>Uses Testcontainers Redis to test the real {@link com.ulticode.common.aspect.RateLimitAspect}
 * with atomic Lua script execution. Each test flushes Redis keys to ensure clean state.</p>
 *
 * <p>Rate limits under test:</p>
 * <ul>
 *   <li>{@code POST /auth/register} — 5 requests per 60 seconds</li>
 *   <li>{@code POST /auth/login} — 10 requests per 60 seconds</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("RateLimitIT")
class RateLimitIT {

    @SuppressWarnings("rawtypes")
    @Container
    private static final GenericContainer REDIS_CONTAINER = new GenericContainer("redis:7-alpine")
            .withExposedPorts(6379);

    @SuppressWarnings("rawtypes")
    @DynamicPropertySource
    static void overrideRedisProperties(DynamicPropertyRegistry registry) {
        GenericContainer container = REDIS_CONTAINER;
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> container.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String FIXED_IP = "127.0.0.1";

    /**
     * Flush all rate-limit keys before each test to ensure clean state.
     */
    @BeforeEach
    void flushRedisKeys() {
        stringRedisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    /**
     * Creates a POST request with JSON content and a consistent X-Real-IP header
     * so that all requests hit the same rate limit bucket.
     */
    private MockHttpServletRequestBuilder jsonPost(String path, Object body) {
        try {
            return post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .header("X-Real-IP", FIXED_IP);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    @Nested
    @DisplayName("POST /auth/register — 5 requests per 60 seconds")
    class RegisterRateLimitTests {

        @Test
        @DisplayName("6th request within 60s window should return 429")
        void register_rateLimitExceeded_returns429() throws Exception {
            RegisterDTO dto = new RegisterDTO();
            dto.setPassword("Test1234");

            // First 5 requests should succeed
            for (int i = 1; i <= 5; i++) {
                dto.setUsername("ratelimit-register-" + System.nanoTime());
                dto.setEmail("ratelimit-" + System.nanoTime() + "@test.com");

                mockMvc.perform(jsonPost("/auth/register", dto))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0));
            }

            // 6th request should hit rate limit
            dto.setUsername("ratelimit-register-" + System.nanoTime());
            dto.setEmail("ratelimit-" + System.nanoTime() + "@test.com");

            MvcResult result = mockMvc.perform(jsonPost("/auth/register", dto))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(42900))
                    .andReturn();

            String message = result.getResponse().getContentAsString();
            // Assert rate limit message is present
            assert message.contains("Rate limit") || message.contains("try again");
        }
    }

    @Nested
    @DisplayName("POST /auth/login — 10 requests per 60 seconds")
    class LoginRateLimitTests {

        @Test
        @DisplayName("11th request within 60s window should return 429")
        void login_rateLimitExceeded_returns429() throws Exception {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("nonexistent-user-" + System.nanoTime());
            dto.setPassword("wrongpassword");

            // First 10 requests should succeed (even with bad credentials, they hit the rate limit check first)
            for (int i = 1; i <= 10; i++) {
                dto.setUsername("ratelimit-login-" + System.nanoTime());

                mockMvc.perform(jsonPost("/auth/login", dto))
                        .andExpect(status().isUnauthorized());  // 401 for bad credentials, not 429
            }

            // 11th request should hit rate limit
            dto.setUsername("ratelimit-login-" + System.nanoTime());

            MvcResult result = mockMvc.perform(jsonPost("/auth/login", dto))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(42900))
                    .andReturn();

            String message = result.getResponse().getContentAsString();
            assert message.contains("Rate limit") || message.contains("try again");
        }
    }
}

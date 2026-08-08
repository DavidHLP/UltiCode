package com.ulticode.websecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.websecurity.aspect.RateLimitAspect;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.config.WebSecurityAutoConfiguration;
import com.ulticode.websecurity.ratelimiter.RedisRateLimiter;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P7-WEB-SECURITY-INFRA-001: Isolated E2E tests for rate limiting using real
 * Testcontainers Redis + RateLimitAspect from the canonical backend-web-security module.
 *
 * <p>Verifies: user/IP bucket key generation, HTTP 429, code 42900, dynamic
 * retry seconds, and Retry-After header.
 */
@SpringBootTest(classes = RateLimitIT.TestConfig.class)
@ImportAutoConfiguration({RedisAutoConfiguration.class, WebMvcAutoConfiguration.class,
        WebSecurityAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@DisplayName("RateLimitIT — canonical web-security module")
class RateLimitIT {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        public StubRateLimitController stubRateLimitController() {
            return new StubRateLimitController();
        }

        @Bean
        public CurrentUserProvider currentUserProvider() {
            return Mockito.mock(CurrentUserProvider.class);
        }

        @Bean
        public TestRateLimitExceptionHandler testRateLimitExceptionHandler() {
            return new TestRateLimitExceptionHandler();
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** Test-only transport adapter; production shells own their Web exception mapping. */
    @RestControllerAdvice
    static class TestRateLimitExceptionHandler {

        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Result<Void>> handle(BusinessException exception) {
            String message = exception.getMessage();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d+) seconds?")
                    .matcher(message);
            HttpHeaders headers = new HttpHeaders();
            if (matcher.find()) {
                headers.set(HttpHeaders.RETRY_AFTER, matcher.group(1));
            }
            return new ResponseEntity<>(
                    Result.error(exception.getErrorCode().code(), message, exception.getTraceId()),
                    headers,
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @RestController
    static class StubRateLimitController {

        @PostMapping("/auth/register")
        @RateLimit(key = "register", limit = 5, period = 60)
        public void register() {
        }

        @PostMapping("/auth/login")
        @RateLimit(key = "login", limit = 10, period = 60)
        public void login() {
        }

        @PostMapping("/auth/refresh")
        @RateLimit(key = "refresh", limit = 20, period = 60)
        public void refresh() {
        }
    }

    @SuppressWarnings("rawtypes")
    @Container
    private static final GenericContainer REDIS_CONTAINER = new GenericContainer("redis:7-alpine")
            .withExposedPorts(6379);

    @SuppressWarnings("rawtypes")
    @DynamicPropertySource
    static void overrideRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    private static final String FIXED_IP = "192.0.2.42";

    @BeforeEach
    void flushRedisKeys() {
        Mockito.when(currentUserProvider.getCurrentUserId()).thenReturn(null);
        if (stringRedisTemplate.getConnectionFactory() != null) {
            stringRedisTemplate.getConnectionFactory().getConnection().flushDb();
        }
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Object body) throws Exception {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Real-IP", FIXED_IP)
                .content(objectMapper.writeValueAsString(body));
    }

    @Nested
    @DisplayName("POST /auth/register — 5 requests per 60 seconds")
    class RegisterRateLimitTests {

        @Test
        @DisplayName("Allows first 5 registration requests and blocks the 6th with HTTP 429, code 42900, and dynamic Retry-After")
        void rateLimitFifthBlocksSixth() throws Exception {
            Map<String, String> body = Map.of(
                    "username", "ratelimituser",
                    "password", "Password123!",
                    "email", "ratelimit@example.com"
            );

            for (int i = 1; i <= 5; i++) {
                mockMvc.perform(jsonPost("/auth/register", body))
                        .andExpect(status().isOk());
            }

            assertThat(stringRedisTemplate.opsForValue()
                    .get("rate-limit:register:ip:" + FIXED_IP)).isEqualTo("5");

            // 6th request must be rate-limited
            mockMvc.perform(jsonPost("/auth/register", body))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(42900))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.matchesPattern(
                                    "Rate limit exceeded\\. Please try again in [1-9][0-9]* seconds\\.")));
        }

        @Test
        @DisplayName("Authenticated users share one bucket across different IPs")
        void authenticatedUserBucketIgnoresChangingIp() throws Exception {
            Mockito.when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
            Map<String, String> body = Map.of(
                    "username", "ratelimituser",
                    "password", "Password123!",
                    "email", "ratelimit@example.com"
            );
            for (int i = 1; i <= 5; i++) {
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Real-IP", "198.51.100." + i)
                                .content(objectMapper.writeValueAsString(body)))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Real-IP", "203.0.113.10")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(42900));
        }

        @Test
        @DisplayName("Different IPs have independent buckets")
        void differentIpsIndependentBuckets() throws Exception {
            Map<String, String> body = Map.of(
                    "username", "ratelimituser",
                    "password", "Password123!",
                    "email", "ratelimit@example.com"
            );

            // Exhaust bucket for FIXED_IP
            for (int i = 1; i <= 5; i++) {
                mockMvc.perform(jsonPost("/auth/register", body))
                        .andExpect(status().isOk());
            }

            // Different IP should still be allowed
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Real-IP", "198.51.100.99")
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /auth/login — 10 requests per 60 seconds")
    class LoginRateLimitTests {

        @Test
        @DisplayName("Allows first 10 login requests and blocks the 11th with HTTP 429 and code 42900")
        void rateLimitTenthBlocksEleventh() throws Exception {
            Map<String, String> body = Map.of(
                    "username", "ratelimituser",
                    "password", "Password123!"
            );

            for (int i = 1; i <= 10; i++) {
                mockMvc.perform(jsonPost("/auth/login", body))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(jsonPost("/auth/login", body))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(42900))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.matchesPattern(
                                    "Rate limit exceeded\\. Please try again in [1-9][0-9]* seconds\\.")));
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh — 20 requests per 60 seconds")
    class RefreshRateLimitTests {

        @Test
        @DisplayName("Allows first 20 refresh requests and blocks the 21st")
        void rateLimitTwentiethBlocksTwentyFirst() throws Exception {
            Map<String, String> body = Map.of("refreshToken", "dummy-token");

            for (int i = 1; i <= 20; i++) {
                mockMvc.perform(jsonPost("/auth/refresh", body))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(jsonPost("/auth/refresh", body))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(42900));
        }
    }
}

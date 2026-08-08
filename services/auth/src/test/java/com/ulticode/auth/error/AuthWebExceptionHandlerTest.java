package com.ulticode.auth.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthWebExceptionHandlerTest {

    private final AuthWebExceptionHandler handler = new AuthWebExceptionHandler();

    @Test
    void springMvcResolvesBothBusinessExceptionTypes() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(handler)
                .build();

        mvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "7"))
                .andExpect(jsonPath("$.code").value(42900));
        mvc.perform(get("/test/auth-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void mapsCommonRateLimitExceptionToResultAndValidatedRetryAfter() {
        BusinessException exception = new BusinessException(
                BaseErrorCode.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Please try again in 42 seconds.",
                "t-rate-limit");

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(42900);
        assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
        assertThat(response.getBody().getTraceId()).isEqualTo("t-rate-limit");
    }

    @Test
    void omitsRetryAfterWhenRateLimitMessageHasNoValidSeconds() {
        BusinessException exception = new BusinessException(
                BaseErrorCode.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Retry later.\r\nX-Injected: true",
                "t-invalid-retry");

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.RETRY_AFTER);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(42900);
    }

    @ParameterizedTest
    @MethodSource("authErrors")
    void preservesAuthErrorCodesAndStatuses(AuthErrorCode errorCode, HttpStatus status) {
        AuthBusinessException exception = new AuthBusinessException(
                errorCode,
                errorCode.message(),
                "t-auth");

        ResponseEntity<Result<Void>> response = handler.handleAuthBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.RETRY_AFTER);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(errorCode.code());
        assertThat(response.getBody().getMessage()).isEqualTo(errorCode.message());
        assertThat(response.getBody().getTraceId()).isEqualTo("t-auth");
    }

    @Test
    void mapsBaseErrorCodeInsideAuthExceptionInsteadOfUsingOkFallback() {
        AuthBusinessException exception = new AuthBusinessException(
                BaseErrorCode.UNAUTHORIZED,
                "Unauthorized",
                "t-base-auth");

        ResponseEntity<Result<Void>> response = handler.handleAuthBusinessException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(40100);
    }

    private static Stream<Arguments> authErrors() {
        return Stream.of(
                Arguments.of(AuthErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED),
                Arguments.of(AuthErrorCode.AUTH_NO_PASSWORD, HttpStatus.UNAUTHORIZED),
                Arguments.of(AuthErrorCode.AUTH_USERNAME_TAKEN, HttpStatus.CONFLICT),
                Arguments.of(AuthErrorCode.AUTH_EMAIL_TAKEN, HttpStatus.CONFLICT),
                Arguments.of(AuthErrorCode.AUTH_USER_NOT_FOUND, HttpStatus.NOT_FOUND),
                Arguments.of(AuthErrorCode.AUTH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED),
                Arguments.of(AuthErrorCode.AUTH_INVALID_RESET_TOKEN, HttpStatus.BAD_REQUEST),
                Arguments.of(AuthErrorCode.AUTH_RESET_TOKEN_ALREADY_USED, HttpStatus.BAD_REQUEST),
                Arguments.of(AuthErrorCode.AUTH_RESET_TOKEN_EXPIRED, HttpStatus.BAD_REQUEST),
                Arguments.of(AuthErrorCode.AUTH_INVALID_REQUEST, HttpStatus.BAD_REQUEST));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/rate-limit")
        void rateLimit() {
            throw new BusinessException(BaseErrorCode.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again in 7 seconds.");
        }

        @GetMapping("/test/auth-error")
        void authError() {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USERNAME_TAKEN);
        }
    }
}

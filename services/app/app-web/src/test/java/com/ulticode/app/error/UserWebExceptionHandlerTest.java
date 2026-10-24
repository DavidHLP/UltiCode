package com.ulticode.app.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserWebExceptionHandlerTest {

    private final UserWebExceptionHandler userHandler = new UserWebExceptionHandler();
    private final ProblemWebExceptionHandler problemHandler = new ProblemWebExceptionHandler();

    @Test
    void userNotFoundMapsToNotFound() {
        assertResponse(userHandler.handleBusinessException(new BusinessException(
                UserErrorCode.USER_NOT_FOUND, null, "user-trace")),
                HttpStatus.NOT_FOUND, 20001, "User not found", "user-trace");
    }

    @ParameterizedTest
    @EnumSource(ProblemErrorCode.class)
    void problemDomainMappingsRemainOwnedByProblemAdvice(ProblemErrorCode code) {
        BusinessException exception = new BusinessException(code, null, "problem-trace");
        assertResponse(problemHandler.handleBusinessException(exception),
                code.httpStatus(), code.code(), code.message(), "problem-trace");
        assertResponse(userHandler.handleBusinessException(exception),
                HttpStatus.INTERNAL_SERVER_ERROR, code.code(), code.message(), "problem-trace");
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, 500", "BAD_REQUEST, 400", "VALIDATION_FAILED, 400",
            "UNAUTHORIZED, 401", "FORBIDDEN, 403", "NOT_FOUND, 404",
            "METHOD_NOT_ALLOWED, 405", "CONFLICT, 409", "TOO_MANY_REQUESTS, 429",
            "UNKNOWN_ERROR, 500", "DATABASE_ERROR, 500"
    })
    void baseMappingsArePreserved(BaseErrorCode code, int status) {
        assertBoth(new BusinessException(code, null, "base-trace"),
                HttpStatus.valueOf(status), code.code(), code.message(), "base-trace");
    }

    @Test
    void userDomainCodeRemainsUnknownToProblemAdvice() {
        assertResponse(problemHandler.handleBusinessException(new BusinessException(
                UserErrorCode.USER_NOT_FOUND, null, "cross-trace")),
                HttpStatus.INTERNAL_SERVER_ERROR, 20001, "User not found", "cross-trace");
    }

    @Test
    void unknownCodesKeepCodeMessageAndInternalServerError() {
        NamespacedErrorCode unknown = new NamespacedErrorCode() {
            @Override
            public String namespace() {
                return "other";
            }

            @Override
            public int code() {
                return 98765;
            }

            @Override
            public String message() {
                return "Other failure";
            }
        };
        assertBoth(new BusinessException(unknown, null, "unknown-trace"),
                HttpStatus.INTERNAL_SERVER_ERROR, 98765, "Other failure", "unknown-trace");
    }

    @Test
    void messageAndTraceFallbacksRemainUnchanged() {
        assertBoth(new BusinessException(BaseErrorCode.BAD_REQUEST, "Custom message", "exception-trace"),
                HttpStatus.BAD_REQUEST, 40000, "Custom message", "exception-trace");
        assertBoth(new BusinessException(BaseErrorCode.BAD_REQUEST, "", "exception-trace"),
                HttpStatus.BAD_REQUEST, 40000, "", "exception-trace");
        assertBoth(new BusinessException(null, "Custom message", "exception-trace"),
                HttpStatus.INTERNAL_SERVER_ERROR, 50000, "Custom message", "exception-trace");
        assertBoth(new BusinessException(null, null, "exception-trace"),
                HttpStatus.INTERNAL_SERVER_ERROR, 50000, "Unknown error", "exception-trace");
        assertBoth(new BusinessException(null, null, (String) null),
                HttpStatus.INTERNAL_SERVER_ERROR, 50000, "Unknown error", null);
    }

    @Test
    void adviceScopesOrderAndHandlerSetsRemainUnchanged() {
        assertArrayEquals(new String[]{"com.ulticode.modules.user"},
                UserWebExceptionHandler.class.getAnnotation(RestControllerAdvice.class).basePackages());
        assertArrayEquals(new String[]{"com.ulticode.modules.problem",
                        "com.ulticode.modules.submission.controller",
                        "com.ulticode.modules.learningplan.controller"},
                ProblemWebExceptionHandler.class.getAnnotation(RestControllerAdvice.class).basePackages());
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
                UserWebExceptionHandler.class.getAnnotation(Order.class).value());
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
                ProblemWebExceptionHandler.class.getAnnotation(Order.class).value());
        assertEquals(1L, Arrays.stream(UserWebExceptionHandler.class.getMethods())
                .filter(method -> method.isAnnotationPresent(ExceptionHandler.class)).count());
        assertEquals(6L, Arrays.stream(ProblemWebExceptionHandler.class.getMethods())
                .filter(method -> method.isAnnotationPresent(ExceptionHandler.class)).count());
    }

    private void assertBoth(BusinessException exception, HttpStatus status, int code,
            String message, String traceId) {
        assertResponse(userHandler.handleBusinessException(exception), status, code, message, traceId);
        assertResponse(problemHandler.handleBusinessException(exception), status, code, message, traceId);
    }

    private void assertResponse(ResponseEntity<Result<Void>> response, HttpStatus status, int code,
            String message, String traceId) {
        assertEquals(status, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals(code, body.getCode());
        assertEquals(message, body.getMessage());
        assertEquals(traceId, body.getTraceId());
        assertNull(body.getData());
    }
}

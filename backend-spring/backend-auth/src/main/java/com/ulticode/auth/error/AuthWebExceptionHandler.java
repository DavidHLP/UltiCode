package com.ulticode.auth.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps auth-service business failures to the shared HTTP {@link Result} envelope. */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ulticode.auth")
public class AuthWebExceptionHandler {

    private static final Pattern RETRY_AFTER_SECONDS = Pattern.compile("(?:^|\\s)(\\d+) seconds?(?:[.,]|$)");

    @ExceptionHandler(AuthBusinessException.class)
    public ResponseEntity<Result<Void>> handleAuthBusinessException(AuthBusinessException exception) {
        return response(exception.getErrorCode(), exception.getMessage(), exception.getTraceId());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        return response(exception.getErrorCode(), exception.getMessage(), exception.getTraceId());
    }

    private ResponseEntity<Result<Void>> response(
            NamespacedErrorCode errorCode,
            String message,
            String traceId) {
        HttpStatus status = httpStatus(errorCode);
        Integer code = errorCode == null ? BaseErrorCode.UNKNOWN_ERROR.code() : errorCode.code();
        String responseMessage = message != null
                ? message
                : errorCode != null ? errorCode.message() : BaseErrorCode.UNKNOWN_ERROR.message();

        log.warn("Business exception: code={}, message={}, traceId={}", code, responseMessage, traceId);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (errorCode == BaseErrorCode.TOO_MANY_REQUESTS) {
            retryAfterSeconds(responseMessage).ifPresent(seconds ->
                    builder.header(HttpHeaders.RETRY_AFTER, seconds));
        }
        return builder.body(Result.error(code, responseMessage, traceId));
    }

    private HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof AuthErrorCode authErrorCode) {
            return authErrorCode.getHttpStatus();
        }
        if (!(errorCode instanceof BaseErrorCode baseErrorCode)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (baseErrorCode) {
            case SUCCESS -> HttpStatus.OK;
            case BAD_REQUEST, VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case UNKNOWN_ERROR, DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private java.util.Optional<String> retryAfterSeconds(String message) {
        Matcher matcher = RETRY_AFTER_SECONDS.matcher(message);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        try {
            long seconds = Long.parseLong(matcher.group(1));
            return java.util.Optional.of(Long.toString(seconds));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }
}

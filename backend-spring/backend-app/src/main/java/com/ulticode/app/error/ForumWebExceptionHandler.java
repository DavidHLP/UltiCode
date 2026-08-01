package com.ulticode.app.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.TraceIdUtil;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps backend-app forum module failures to the shared HTTP result envelope.
 *
 * <p>Mirrors {@link SolutionWebExceptionHandler}: bounded to forum controller
 * packages via {@link RestControllerAdvice#basePackages()}, explicit
 * {@link NamespacedErrorCode}→HTTP mapping via {@link ForumErrorCode#httpStatus()},
 * and only handlers needed by forum controllers.
 *
 * <p>P7-RELOCATE-FORUM-001: required when the forum family relocated
 * from backend-legacy to backend-app.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ulticode.modules.forum")
public class ForumWebExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        NamespacedErrorCode errorCode = exception.getErrorCode();
        Integer code = errorCode == null ? BaseErrorCode.UNKNOWN_ERROR.code() : errorCode.code();
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : errorCode != null ? errorCode.message() : BaseErrorCode.UNKNOWN_ERROR.message();
        HttpStatus status = httpStatus(errorCode);
        log.warn("Business exception: code={}, message={}, traceId={}", code, message, exception.getTraceId());
        return ResponseEntity.status(status).body(Result.error(code, message, exception.getTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            errors.put(fieldName, error.getDefaultMessage());
        });
        String traceId = TraceIdUtil.current();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.errorWithData(
                BaseErrorCode.BAD_REQUEST.code(), "Validation failed", errors, traceId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        String traceId = TraceIdUtil.current();
        log.warn("Malformed request body: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(
                BaseErrorCode.BAD_REQUEST.code(), "Malformed request body", traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String expected = exception.getRequiredType() != null
                ? exception.getRequiredType().getSimpleName()
                : "?";
        String message = "Invalid value for parameter '" + exception.getName()
                + "': expected " + expected;
        log.warn("Type mismatch: {}", message);
        String traceId = TraceIdUtil.current();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(
                BaseErrorCode.BAD_REQUEST.code(), message, traceId));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        String traceId = TraceIdUtil.current();
        log.warn("Access denied: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(
                BaseErrorCode.FORBIDDEN.code(), BaseErrorCode.FORBIDDEN.message(), traceId));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(AuthenticationException exception) {
        String traceId = TraceIdUtil.current();
        log.warn("Authentication failed: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(
                BaseErrorCode.UNAUTHORIZED.code(), BaseErrorCode.UNAUTHORIZED.message(), traceId));
    }

    private HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof ForumErrorCode forumErrorCode) {
            return forumErrorCode.httpStatus();
        }
        if (errorCode instanceof BaseErrorCode baseErrorCode) {
            return switch (baseErrorCode) {
                case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
                case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
                case BAD_REQUEST, VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
                case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

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
 * Maps backend-app Problem failures to the shared HTTP result envelope.
 *
 * <p>The advice is bounded to the Problem controller package so it cannot
 * alter error handling for unrelated app families.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ulticode.modules.problem")
public class ProblemWebExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        NamespacedErrorCode errorCode = exception.getErrorCode();
        Integer code = errorCode == null ? BaseErrorCode.UNKNOWN_ERROR.code() : errorCode.code();
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : errorCode != null ? errorCode.message() : BaseErrorCode.UNKNOWN_ERROR.message();
        return ResponseEntity.status(httpStatus(errorCode)).body(
                Result.error(code, message, exception.getTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            errors.put(fieldName, error.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.errorWithData(
                BaseErrorCode.BAD_REQUEST.code(), "Validation failed", errors, TraceIdUtil.current()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(
                BaseErrorCode.BAD_REQUEST.code(), "Malformed request body", TraceIdUtil.current()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String expected = exception.getRequiredType() != null
                ? exception.getRequiredType().getSimpleName()
                : "?";
        String message = "Invalid value for parameter '" + exception.getName()
                + "': expected " + expected;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(
                BaseErrorCode.BAD_REQUEST.code(), message, TraceIdUtil.current()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(
                BaseErrorCode.FORBIDDEN.code(), BaseErrorCode.FORBIDDEN.message(), TraceIdUtil.current()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(
                BaseErrorCode.UNAUTHORIZED.code(), BaseErrorCode.UNAUTHORIZED.message(), TraceIdUtil.current()));
    }

    private HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof ProblemErrorCode problemErrorCode) {
            return problemErrorCode.httpStatus();
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

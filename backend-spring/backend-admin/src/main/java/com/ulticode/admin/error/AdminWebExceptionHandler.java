package com.ulticode.admin.error;

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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps backend-admin business failures to the shared HTTP result envelope. */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {"com.ulticode.admin", "com.ulticode.modules.admin"})
public class AdminWebExceptionHandler {

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
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception) {
        String traceId = TraceIdUtil.current();
        log.warn("Malformed request body: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(
                BaseErrorCode.BAD_REQUEST.code(), "Malformed request body", traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        String expected = exception.getRequiredType() == null
                ? "unknown"
                : exception.getRequiredType().getSimpleName();
        String traceId = TraceIdUtil.current();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.error(
                BaseErrorCode.BAD_REQUEST.code(),
                "Invalid value for parameter '" + exception.getName() + "' (expected " + expected + ")",
                traceId));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(
                BaseErrorCode.FORBIDDEN.code(), BaseErrorCode.FORBIDDEN.message()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(
                BaseErrorCode.UNAUTHORIZED.code(), BaseErrorCode.UNAUTHORIZED.message()));
    }

    private HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof AdminErrorCode adminErrorCode) {
            return adminErrorCode.getHttpStatus();
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
}

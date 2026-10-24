package com.ulticode.app.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.TraceIdUtil;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Shared handlers; only the scoped Forum and Solution subclasses are registered as advice. */
public abstract class ContentWebExceptionHandler {

    private final Logger log;

    protected ContentWebExceptionHandler(Logger log) {
        this.log = log;
    }

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

    protected HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        return BusinessExceptionResponse.baseHttpStatus(errorCode);
    }
}

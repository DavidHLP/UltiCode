package com.ulticode.common.exception;

import com.ulticode.common.response.Result;
import com.ulticode.common.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 * Converts exceptions into standardized Result responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle BusinessException
     * Returns a Result with the appropriate error code and HTTP status
     *
     * @param ex the BusinessException
     * @return ResponseEntity containing the error Result
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: code={}, message={}, traceId={}",
                ex.getCode(), ex.getMessage(), ex.getTraceId());

        Result<Void> result = Result.error(ex.getCode(), ex.getMessage(), ex.getTraceId());

        HttpStatus status = ex.getHttpStatus();
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);

        // Add Retry-After header for rate limit responses
        if (ex.getHttpStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            String message = ex.getMessage();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+) seconds?").matcher(message);
            if (matcher.find()) {
                responseBuilder.header(HttpHeaders.RETRY_AFTER, matcher.group(1));
            }
        }

        return responseBuilder.body(result);
    }

    /**
     * Handle validation errors from @Valid annotations
     * Returns a Result with BAD_REQUEST status and field error details
     *
     * @param ex the MethodArgumentNotValidException
     * @return ResponseEntity containing the error Result
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        String traceId = TraceIdUtil.current();
        Result<Map<String, String>> result = Result.errorWithData(
                ErrorCode.BAD_REQUEST.getCode(),
                "Validation failed",
                errors,
                traceId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * Handle AccessDeniedException from Spring Security
     * Returns a Result with FORBIDDEN status
     *
     * @param ex the AccessDeniedException
     * @return ResponseEntity containing the error Result
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        String traceId = TraceIdUtil.current();
        Result<Void> result = Result.error(
                ErrorCode.FORBIDDEN.getCode(),
                ErrorCode.FORBIDDEN.getMessage(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    /**
     * Handle AuthenticationException from Spring Security
     * Returns a Result with UNAUTHORIZED status
     *
     * @param ex the AuthenticationException
     * @return ResponseEntity containing the error Result
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());

        String traceId = TraceIdUtil.current();
        Result<Void> result = Result.error(
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getMessage(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * Handle all other exceptions
     * Returns a Result with INTERNAL_SERVER_ERROR status
     *
     * @param ex the Exception
     * @return ResponseEntity containing the error Result
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        String traceId = TraceIdUtil.current();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ErrorCode.NOT_FOUND.getCode(), "Not found", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);

        String traceId = TraceIdUtil.current();
        Result<Void> result = Result.error(
                ErrorCode.UNKNOWN_ERROR.getCode(),
                ErrorCode.UNKNOWN_ERROR.getMessage(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());

        String traceId = TraceIdUtil.current();
        Result<Void> result = Result.error(
                ErrorCode.METHOD_NOT_ALLOWED.getCode(),
                "Method not allowed: " + ex.getMethod(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(result);
    }

    /**
     * Handle OptimisticLockException from MyBatis-Plus @Version
     * Returns a Result with CONFLICT status and current version info
     *
     * @param ex the OptimisticLockException
     * @return ResponseEntity containing the error Result
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Result<Map<String, Object>>> handleOptimisticLockException(OptimisticLockException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());

        String traceId = TraceIdUtil.current();

        Map<String, Object> data = new HashMap<>();
        data.put("currentVersion", ex.getCurrentVersion());

        Result<Map<String, Object>> result = Result.errorWithData(
                409,
                "版本冲突",
                data,
                traceId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }
}

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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.mybatis.spring.MyBatisSystemException;

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

    /**
     * Map MyBatis/MyBatis-Plus persistence-layer failures to a dedicated error code
     * (50001) rather than the generic 50000 "Unknown error" returned by
     * {@link #handleGenericException}.  This preserves the original traceId so an
     * operator can correlate the client response to the full server-side stack
     * trace, which previously got swallowed as "Unknown error" (see
     * docs/forum-api-curl-test-report-2026-06-08.md §3).
     *
     * <p>The fix for the underlying LocalDateTime serialization issue lives in
     * {@link com.ulticode.common.config.JacksonConfig#objectMapper()}.</p>
     */
    @ExceptionHandler(MyBatisSystemException.class)
    public ResponseEntity<Result<Void>> handleMyBatisSystemException(MyBatisSystemException ex) {
        String traceId = TraceIdUtil.current();
        // Log the full cause chain server-side (operator action); only surface a
        // generic message to the client to avoid leaking internal class names
        // (e.g. java.sql.SQLException, Jackson InvalidDefinitionException) and
        // any column/SQL fragments embedded in the message.
        log.error("MyBatis persistence error (traceId={}, rootCause={}: {})",
                traceId, rootCauseClassName(ex), rootCauseMessage(ex), ex);

        Result<Void> result = Result.error(
                ErrorCode.DATABASE_ERROR.getCode(),
                ErrorCode.DATABASE_ERROR.getMessage(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
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

    /**
     * Walk the cause chain and return the deepest non-null message. Server-side
     * only — never expose to clients (use {@link ErrorCode#DATABASE_ERROR} instead)
     * to avoid leaking internal exception class names or SQL fragments.
     */
    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }

    /**
     * Walk the cause chain and return the deepest class name. Server-side only —
     * used in log lines so operators can grep on exception type.
     */
    private static String rootCauseClassName(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getName();
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
    /**
     * Handle ConstraintViolationException thrown by @PathVariable / @RequestParam
     * constraints (e.g. {@code @Pattern}, {@code @Size}) on controllers annotated with
     * {@code @Validated}. Without this handler the exception falls through to
     * {@link #handleGenericException} and returns HTTP 500. Mirrors the response shape
     * of {@link #handleValidationException} (which handles {@code @Valid @RequestBody}).
     *
     * @param ex the ConstraintViolationException
     * @return ResponseEntity with HTTP 400 and per-field error map
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = v.getPropertyPath().toString();
            String fieldName = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(fieldName, v.getMessage());
        }
        log.warn("Constraint violation: {}", errors);
        String traceId = TraceIdUtil.current();
        Result<Map<String, String>> result = Result.errorWithData(
                ErrorCode.BAD_REQUEST.getCode(), "Validation failed", errors, traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

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

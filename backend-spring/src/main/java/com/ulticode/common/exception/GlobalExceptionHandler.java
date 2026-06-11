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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.apache.ibatis.binding.BindingException;
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

    /**
     * Handle MyBatis BindingException — raised when a mapper annotation/SQL
     * mismatch causes MyBatis to return {@code null} for a primitive return
     * type (e.g. {@code @Select} used with a DELETE statement on a method
     * returning {@code int}).
     *
     * <p>Maps to {@code DATABASE_ERROR} instead of leaking through the
     * catch-all {@link #handleGenericException}, which would return
     * "Unknown error" (50000) and mask the real cause. The Spring-managed
     * {@link MyBatisSystemException} handler above does NOT catch this
     * exception, because {@link BindingException} extends MyBatis'
     * {@code PersistenceException} directly and bypasses Spring's translation
     * layer. (Reported in
     * {@code docs/bookmark-api-test-report-2026-06-11.md} §T08, T10.)
     *
     * @param ex the BindingException
     * @return 500 with {@code DATABASE_ERROR} code; full stack logged server-side
     */
    @ExceptionHandler(BindingException.class)
    public ResponseEntity<Result<Void>> handleBindingException(BindingException ex) {
        String traceId = TraceIdUtil.current();
        log.error("MyBatis binding error (traceId={}, rootCause={}: {})",
                traceId, rootCauseClassName(ex), rootCauseMessage(ex), ex);

        Result<Void> result = Result.error(
                ErrorCode.DATABASE_ERROR.getCode(),
                ErrorCode.DATABASE_ERROR.getMessage(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * Map Spring's {@link BadSqlGrammarException} (thrown by JDBC exception
     * translator when SQL is invalid — e.g. MySQL reserved word unquoted in
     * {@code @Select} annotation, or auto-generated column list referencing a
     * reserved word) to a dedicated error code. Without this handler the
     * exception falls through to {@link #handleGenericException} which returns
     * 50000 "Unknown error" and masks the SQL root cause, making it impossible
     * to debug from the response body alone.
     *
     * <p>{@code BadSqlGrammarException} extends Spring's {@code DataAccessException}
     * directly, NOT MyBatis's {@code MyBatisSystemException}, so the existing
     * MyBatis handler above does NOT catch it. (Reported in
     * docs/achievement-api-test-report-2026-06-11.md §6 MEDIUM #4.)</p>
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<Result<Void>> handleBadSqlGrammarException(BadSqlGrammarException ex) {
        String traceId = TraceIdUtil.current();
        log.error("Bad SQL grammar (traceId={}, rootCause={}: {})",
                traceId, rootCauseClassName(ex), rootCauseMessage(ex), ex);
        Result<Void> result = Result.error(
                ErrorCode.DATABASE_ERROR.getCode(),
                ErrorCode.DATABASE_ERROR.getMessage(),
                traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGenericException(Exception ex) {
        String traceId = TraceIdUtil.current();
        // 显式把 traceId 放进日志前缀，方便 grep 关联请求
        log.error("Unexpected error (traceId={}, rootCause={}: {})",
                traceId, rootCauseClassName(ex), rootCauseMessage(ex), ex);

        Result<Void> result = Result.error(
                ErrorCode.UNKNOWN_ERROR.getCode(),
                ErrorCode.UNKNOWN_ERROR.getMessage(),
                traceId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理 MySQL 唯一约束冲突 / FK 冲突等数据完整性错误。
     * 作为 createInitialVersion 等查重的二线防御：若 service 层未来漏掉 DuplicateKeyException 检查，
     * 仍能返回 409 而非 50000 "Unknown error"。
     *
     * <p>根据 rootCause 消息区分场景：
     * <ul>
     *   <li>Duplicate entry  → 409 + 30004（与 service 层首线防御一致）</li>
     *   <li>FK / NOT NULL  → 409 + CONFLICT(40900) + 通用 message</li>
     * </ul>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String traceId = TraceIdUtil.current();
        String rootMsg = rootCauseMessage(ex);
        log.warn("Data integrity violation (traceId={}, rootCause={}: {})",
                traceId, rootCauseClassName(ex), rootMsg, ex);

        boolean isDuplicate = rootMsg != null
                && rootMsg.toLowerCase().contains("duplicate entry");

        int code = isDuplicate
                ? ErrorCode.PROBLEM_VERSION_ALREADY_EXISTS.getCode()  // 30004
                : ErrorCode.CONFLICT.getCode();                          // 40900
        String message = isDuplicate
                ? "Duplicate resource"
                : "Data integrity violation (FK or NOT NULL constraint)";

        Result<Void> result = Result.error(code, message, traceId);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
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

    /**
     * Map a missing required {@code @RequestParam} (e.g. {@code GET /admin/tags/{id}}
     * without {@code ?type=}) to HTTP 400 with a per-field error. Without this handler
     * Spring throws {@link MissingServletRequestParameterException} to the catch-all
     * {@link #handleGenericException} which returns HTTP 500 / code=50000 "Unknown
     * error" — reported in docs/admin-tags-test-plan.md §7 Bug #1.
     *
     * <p>Mirrors the response shape of {@link #handleValidationException} (which
     * handles {@code @Valid @RequestBody}) and {@link #handleConstraintViolation}
     * (which handles {@code @Validated} {@code @Pattern} / {@code @Size} on params).
     * </p>
     *
     * @param ex the missing-param failure
     * @return 400 with the parameter name and an explanatory message
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Map<String, String>>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getParameterName(),
                "Missing required parameter '" + ex.getParameterName()
                        + "' (type=" + ex.getParameterType() + ")");
        log.warn("Missing request parameter: {}", errors);

        String traceId = TraceIdUtil.current();
        Result<Map<String, String>> result = Result.errorWithData(
                ErrorCode.BAD_REQUEST.getCode(), "Validation failed", errors, traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

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

    /**
     * Map Jackson deserialization failures (malformed JSON, wrong body shape,
     * missing required body) to HTTP 400 with a descriptive message. Without
     * this handler these errors fall through to the catch-all
     * {@link #handleGenericException} and return HTTP 500, which both
     * mis-classifies the response (it's a client error) and pollutes 5xx
     * alerting. (Reported in docs/SETTINGS_API_TEST_REPORT_2026-06-09.md
     * §5.3.)
     *
     * @param ex the Jackson failure
     * @return 400 with a single-line, operator-friendly message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String traceId = TraceIdUtil.current();

        // Detect Jackson enum-conversion failures: root cause is
        // com.fasterxml.jackson.databind.exc.InvalidFormatException with
        // a Class<?> targetType that is an Enum. Strip the verbose
        // "not one of the values accepted for Enum class: [...]" list
        // to avoid leaking the full backend enum surface to clients.
        // (Reported in docs/edge-operations-api-test-report-2026-06-11.md §4.3.)
        Throwable root = ex.getCause();
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            String fieldPath = ife.getPath().isEmpty()
                    ? "body"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String compact = "Invalid value for parameter '" + fieldPath + "'";
            log.warn("Malformed enum in body: path={}, targetType={}, value={}",
                    fieldPath, ife.getTargetType().getSimpleName(), ife.getValue());
            Result<Void> result = Result.error(
                    ErrorCode.BAD_REQUEST.getCode(), compact, traceId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        // Fallback: existing behavior for non-enum Jackson failures
        // Trim Jackson's verbose cause chain to the first line so the response
        // payload stays compact. Full stack is still logged server-side.
        String rootMsg = rootCauseMessage(ex);
        String compact = rootMsg != null
                ? rootMsg.split("\\R", 2)[0]
                : ex.getMessage();
        log.warn("Malformed request body: {}", compact);

        Result<Void> result = Result.error(
                ErrorCode.BAD_REQUEST.getCode(),
                "Malformed request body: " + compact,
                traceId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * Map Spring's type-conversion failures (e.g. putting {@code "abc"} into
     * a {@code Long} path variable) to HTTP 400. Same rationale as
     * {@link #handleHttpMessageNotReadable}.
     *
     * @param ex the conversion failure
     * @return 400 with the parameter name and the expected type
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "?";
        String message = "Invalid value for parameter '" + ex.getName()
                + "': expected " + expected;
        log.warn("Type mismatch: {}", message);

        String traceId = TraceIdUtil.current();
        Result<Void> result = Result.error(
                ErrorCode.BAD_REQUEST.getCode(),
                message,
                traceId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
}

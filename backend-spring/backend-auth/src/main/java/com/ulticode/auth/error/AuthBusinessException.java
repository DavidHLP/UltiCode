package com.ulticode.auth.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.util.TraceIdUtil;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business exception that carries a {@link NamespacedErrorCode} and a
 * trace ID. Private to backend-auth: backend-legacy has its own
 * {@code com.ulticode.common.exception.BusinessException} which
 * depends on the legacy HTTP envelope's error code enum and is
 * intentionally not exported to backend-common.
 *
 * <p>Accepts the {@link NamespacedErrorCode} interface so callers can
 * throw either the auth-module-local {@link AuthErrorCode} or the
 * shared {@link BaseErrorCode} (UNAUTHORIZED, FORBIDDEN, etc.) without
 * the exception type coupling them to a single enum.
 *
 * <p>{@link #getHttpStatus()} returns {@code HttpStatus.OK} (200) when
 * the supplied error code does not carry an HTTP mapping (e.g. a
 * backend-common {@code BaseErrorCode} passed in here). The auth
 * service's exception handler is responsible for mapping the
 * exception back to a wire-level error: the {@code code()} and
 * {@code message()} come from the {@link NamespacedErrorCode}, the
 * {@code namespace()} is used by the cross-service envelope.
 */
@Getter
public class AuthBusinessException extends RuntimeException {

    private final NamespacedErrorCode errorCode;
    private final String traceId;

    public AuthBusinessException(NamespacedErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public AuthBusinessException(NamespacedErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public AuthBusinessException(NamespacedErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public AuthBusinessException(NamespacedErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public AuthBusinessException(NamespacedErrorCode errorCode, String message, String traceId) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    /**
     * Integer business code of the underlying error code.
     */
    public Integer getCode() {
        return errorCode.code();
    }

    /**
     * HTTP status, if the underlying {@link NamespacedErrorCode} carries
     * one (for example {@link AuthErrorCode#getHttpStatus()}). Falls back
     * to {@code OK} when the implementation does not declare an HTTP
     * mapping (notably {@link BaseErrorCode}); the auth service's
     * exception handler is responsible for the final mapping.
     */
    public HttpStatus getHttpStatus() {
        if (errorCode instanceof AuthErrorCode) {
            return ((AuthErrorCode) errorCode).getHttpStatus();
        }
        return HttpStatus.OK;
    }
}

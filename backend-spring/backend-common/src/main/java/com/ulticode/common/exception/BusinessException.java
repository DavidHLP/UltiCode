package com.ulticode.common.exception;

import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.util.TraceIdUtil;
import lombok.Getter;

/**
 * Base business exception in backend-common carrying a {@link NamespacedErrorCode} and trace ID.
 * Free of Spring dependencies so it can be safely used across all service contracts (P2-DISC-001).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final NamespacedErrorCode errorCode;
    private final String traceId;

    public BusinessException(NamespacedErrorCode errorCode) {
        super(errorCode != null ? errorCode.message() : null);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public BusinessException(NamespacedErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public BusinessException(NamespacedErrorCode errorCode, Throwable cause) {
        super(errorCode != null ? errorCode.message() : null, cause);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public BusinessException(NamespacedErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    public BusinessException(NamespacedErrorCode errorCode, String message, String traceId) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    public Integer getCode() {
        return errorCode != null ? errorCode.code() : null;
    }
}

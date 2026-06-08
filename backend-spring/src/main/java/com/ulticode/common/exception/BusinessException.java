package com.ulticode.common.exception;

import com.ulticode.common.util.TraceIdUtil;
import lombok.Getter;

/**
 * Business exception that carries an error code and trace ID.
 * Used throughout the application for business logic errors.
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * The error code for this exception
     */
    private final ErrorCode errorCode;

    /**
     * Trace ID for request tracking
     */
    private final String traceId;

    /**
     * Create a BusinessException with an error code
     *
     * @param errorCode the error code
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    /**
     * Create a BusinessException with an error code and custom message
     *
     * @param errorCode the error code
     * @param message   custom error message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    /**
     * Create a BusinessException with an error code and cause
     *
     * @param errorCode the error code
     * @param cause     the cause of this exception
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    /**
     * Create a BusinessException with an error code, custom message, and cause
     *
     * @param errorCode the error code
     * @param message   custom error message
     * @param cause     the cause of this exception
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.traceId = TraceIdUtil.current();
    }

    /**
     * Create a BusinessException with an error code and custom trace ID
     *
     * @param errorCode the error code
     * @param message   custom error message
     * @param traceId   custom trace ID
     */
    public BusinessException(ErrorCode errorCode, String message, String traceId) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    /**
     * Get the error code value
     *
     * @return the error code integer value
     */
    public Integer getCode() {
        return errorCode.getCode();
    }

    /**
     * Get the HTTP status for this error
     *
     * @return the HTTP status
     */
    public org.springframework.http.HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}

package com.ulticode.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ulticode.common.util.TraceIdUtil;
import lombok.Data;

/**
 * Generic response wrapper for all API responses.
 * Matches NestJS response format exactly for frontend compatibility.
 *
 * @param <T> the type of data in the response
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /**
     * Response code (0 for success, error code otherwise)
     */
    private Integer code;

    /**
     * Response message
     */
    private String message;

    /**
     * Response data payload
     */
    private T data;

    /**
     * Trace ID for request tracking
     */
    private String traceId;

    private Result() {
    }

    private Result(Integer code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    /**
     * Create a success response with data
     *
     * @param data the data to include
     * @param <T>  the type of data
     * @return success Result with data
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data, TraceIdUtil.current());
    }

    /**
     * Create a success response without data
     *
     * @param <T> the type of data (Void)
     * @return success Result without data
     */
    public static <T> Result<T> success() {
        return new Result<>(0, "success", null, TraceIdUtil.current());
    }

    /**
     * Create an error response with code and message
     *
     * @param code    the error code
     * @param message the error message
     * @param <T>     the type of data
     * @return error Result
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null, TraceIdUtil.current());
    }

    /**
     * Create an error response with code, message, and traceId
     *
     * @param code    the error code
     * @param message the error message
     * @param traceId the trace ID
     * @param <T>     the type of data
     * @return error Result
     */
    public static <T> Result<T> error(Integer code, String message, String traceId) {
        return new Result<>(code, message, null, traceId);
    }

    /**
     * Create an error response with code, message, data, and traceId
     *
     * @param code    the error code
     * @param message the error message
     * @param data    the data payload
     * @param traceId the trace ID
     * @param <T>     the type of data
     * @return error Result
     */
    public static <T> Result<T> errorWithData(Integer code, String message, T data, String traceId) {
        return new Result<>(code, message, data, traceId);
    }
}

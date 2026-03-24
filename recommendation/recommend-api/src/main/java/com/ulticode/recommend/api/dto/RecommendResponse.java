package com.ulticode.recommend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Generic response wrapper for recommendation service.
 *
 * @param <T> the type of data contained in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Indicates whether the request was successful.
     */
    private boolean success;

    /**
     * Response code (0 for success, non-zero for errors).
     */
    private int code;

    /**
     * Human-readable message describing the result.
     */
    private String message;

    /**
     * The response data payload.
     */
    private T data;

    // ========== Static factory methods ==========

    /**
     * Creates a successful response with data.
     *
     * @param data the response data
     * @param <T>  the type of data
     * @return successful response
     */
    public static <T> RecommendResponse<T> success(T data) {
        return RecommendResponse.<T>builder()
                .success(true)
                .code(0)
                .message("Success")
                .data(data)
                .build();
    }

    /**
     * Creates a successful response with data and custom message.
     *
     * @param data    the response data
     * @param message the success message
     * @param <T>     the type of data
     * @return successful response
     */
    public static <T> RecommendResponse<T> success(T data, String message) {
        return RecommendResponse.<T>builder()
                .success(true)
                .code(0)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Creates a failed response with error code and message.
     *
     * @param code    the error code
     * @param message the error message
     * @param <T>     the type of data
     * @return failed response
     */
    public static <T> RecommendResponse<T> fail(int code, String message) {
        return RecommendResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(null)
                .build();
    }

    /**
     * Creates a failed response with message (default code: -1).
     *
     * @param message the error message
     * @param <T>     the type of data
     * @return failed response
     */
    public static <T> RecommendResponse<T> fail(String message) {
        return fail(-1, message);
    }
}

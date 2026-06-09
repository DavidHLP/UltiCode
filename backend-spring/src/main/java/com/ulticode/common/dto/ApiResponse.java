package com.ulticode.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ulticode.common.exception.ErrorCode;

/**
 * Standard API response wrapper for all REST endpoints.
 *
 * @param <T> the type of data payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success, T data, String message, ErrorCode errorCode, Object metadata) {

  /**
   * Creates a successful response with data.
   *
   * @param data the response data
   * @param <T> the type of data
   * @return successful ApiResponse
   */
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, null, null);
  }

  /**
   * Creates a successful response with data and message.
   *
   * @param data the response data
   * @param message the success message
   * @param <T> the type of data
   * @return successful ApiResponse
   */
  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, message, null, null);
  }

  /**
   * Creates a successful response with data and pagination metadata.
   *
   * @param data the response data
   * @param total total number of items
   * @param page current page number
   * @param limit items per page
   * @param <T> the type of data
   * @return successful ApiResponse with pagination metadata
   */
  public static <T> ApiResponse<T> success(T data, long total, int page, int limit) {
    return new ApiResponse<>(true, data, null, null, new PaginationMeta(total, page, limit));
  }

  /**
   * Creates an error response.
   *
   * @param errorCode the error code
   * @param message the error message
   * @param <T> the type of data (null for error)
   * @return error ApiResponse
   */
  public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
    return new ApiResponse<>(false, null, message, errorCode, null);
  }

  /**
   * Creates an error response with just the error code.
   *
   * @param errorCode the error code
   * @param <T> the type of data (null for error)
   * @return error ApiResponse
   */
  public static <T> ApiResponse<T> error(ErrorCode errorCode) {
    return new ApiResponse<>(false, null, errorCode.name(), errorCode, null);
  }

  /** Pagination metadata for list responses. */
  public record PaginationMeta(long total, int page, int limit) {}
}

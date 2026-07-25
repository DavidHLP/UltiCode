package com.ulticode.modules.websocket.dto;

import com.ulticode.common.exception.ErrorCode;

/** WebSocket error message sent to clients. */
public record WebSocketErrorMessage(boolean success, String error, String message) {

  /**
   * Create an error message from error code.
   *
   * @param errorCode the error code
   * @param message the error message
   * @return error message
   */
  public static WebSocketErrorMessage from(ErrorCode errorCode, String message) {
    return new WebSocketErrorMessage(false, errorCode.name(), message);
  }

  /**
   * Create an error message with custom error string.
   *
   * @param error the error string
   * @param message the error message
   * @return error message
   */
  public static WebSocketErrorMessage of(String error, String message) {
    return new WebSocketErrorMessage(false, error, message);
  }
}

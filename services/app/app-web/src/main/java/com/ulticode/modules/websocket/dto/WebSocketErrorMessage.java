package com.ulticode.modules.websocket.dto;

import com.ulticode.app.error.WebSocketErrorCode;
import com.ulticode.common.error.BaseErrorCode;

/** WebSocket error message sent to clients. */
public record WebSocketErrorMessage(boolean success, String error, String message) {

  /**
   * Create an error message from error code.
   *
   * @param errorCode the error code
   * @param message the error message
   * @return error message
   */
  public static WebSocketErrorMessage from(com.ulticode.common.error.NamespacedErrorCode errorCode, String message) {
    return new WebSocketErrorMessage(false, errorCode.namespace() + ":" + errorCode.code(), message);
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

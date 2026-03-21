package com.ulticode.modules.websocket.handler;

import com.ulticode.common.constants.ErrorCode;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.dto.WebSocketErrorMessage;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Global exception handler for WebSocket messages.
 *
 * <p>Catches exceptions from @MessageMapping methods and sends appropriate error responses.
 */
@ControllerAdvice
public class WebSocketExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

  /**
   * Handle WebSocket authentication exceptions.
   *
   * @param e the exception
   * @return error message to send to user
   */
  @MessageExceptionHandler(WebSocketAuthenticationException.class)
  @SendToUser(WebSocketConstants.USER_QUEUE_ERRORS)
  public WebSocketErrorMessage handleAuthenticationException(
      JwtChannelInterceptor.WebSocketAuthenticationException e) {

    log.warn("WebSocket authentication error: {}", e.getMessage());

    return WebSocketErrorMessage.from(e.getErrorCode(), e.getMessage());
  }

  /**
   * Handle generic exceptions.
   *
   * @param e the exception
   * @return error message to send to user
   */
  @MessageExceptionHandler(Exception.class)
  @SendToUser(WebSocketConstants.USER_QUEUE_ERRORS)
  public WebSocketErrorMessage handleException(Exception e) {
    log.error("WebSocket error: {}", e.getMessage(), e);

    return WebSocketErrorMessage.from(ErrorCode.UNKNOWN_ERROR, "An unexpected error occurred");
  }
}

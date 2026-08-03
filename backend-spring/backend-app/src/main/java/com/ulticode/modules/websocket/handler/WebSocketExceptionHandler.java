package com.ulticode.modules.websocket.handler;

import com.ulticode.app.error.WebSocketErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.dto.WebSocketErrorMessage;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Global exception handler for WebSocket messages.
 *
 * <p>Catches exceptions from @MessageMapping methods and sends appropriate error responses.
 *
 * <p>Note: Authentication exceptions during CONNECT are handled automatically by Spring
 * WebSocket - they result in STOMP ERROR frames being sent to the client. This handler only
 * processes exceptions that occur after a successful connection is established.
 */
@Controller
@ControllerAdvice
public class WebSocketExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

  /**
   * Handle WebSocket authentication exceptions.
   *
   * <p>This catches authentication failures from JwtChannelInterceptor.
   *
   * @param e the authentication exception
   * @param headerAccessor the message header accessor
   * @return error message to send to user
   */
  @MessageExceptionHandler(WebSocketAuthenticationException.class)
  @SendToUser(WebSocketConstants.USER_QUEUE_ERRORS)
  public WebSocketErrorMessage handleAuthenticationException(
      WebSocketAuthenticationException e, SimpMessageHeaderAccessor headerAccessor) {
    log.error("=== WebSocketAuthenticationException caught ===");
    log.error("Error Code: {}, Message: {}", e.getErrorCode(), e.getMessage());
    log.error("Stack trace:", e);

    if (headerAccessor != null) {
      log.error("Session ID: {}, User: {}", 
          headerAccessor.getSessionId(), 
          headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "null");
      
      if (headerAccessor.getSessionAttributes() != null) {
        log.error("Session attributes: {}", headerAccessor.getSessionAttributes().keySet());
      }
    }

    return WebSocketErrorMessage.from(e.getErrorCode(), e.getMessage());
  }

  /**
   * Handle generic exceptions from @MessageMapping methods.
   *
   * <p>Authentication exceptions during CONNECT are handled by the framework automatically.
   * This handler only catches exceptions from message handlers that execute after connection.
   *
   * @param e the exception
   * @param headerAccessor the message header accessor
   * @return error message to send to user
   */
  @MessageExceptionHandler(Exception.class)
  @SendToUser(WebSocketConstants.USER_QUEUE_ERRORS)
  public WebSocketErrorMessage handleException(Exception e, SimpMessageHeaderAccessor headerAccessor) {
    log.error("=== WebSocket generic exception caught ===");
    log.error("Exception: {} - {}", e.getClass().getName(), e.getMessage());
    log.error("Stack trace:", e);

    // Log user session info for debugging
    if (headerAccessor != null) {
      log.error("Session ID: {}, User: {}", 
          headerAccessor.getSessionId(), 
          headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "null");
      
      Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
      if (sessionAttrs != null) {
        Object userObj = sessionAttrs.get("user");
        if (userObj instanceof SocketClientData userData) {
          log.error("User from session: {}, sessionId: {}", userData.username(), headerAccessor.getSessionId());
        }
      }
    }

    return WebSocketErrorMessage.from(BaseErrorCode.UNKNOWN_ERROR, "An unexpected error occurred. Please try again.");
  }
}

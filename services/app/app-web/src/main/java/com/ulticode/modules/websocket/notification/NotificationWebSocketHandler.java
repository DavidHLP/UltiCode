package com.ulticode.modules.websocket.notification;

import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.dto.WebSocketErrorMessage;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * WebSocket handler for notification events.
 *
 * <p>Handles notification-specific WebSocket operations including:
 *
 * <ul>
 *   <li>Community subscriptions
 *   <li>Personal notification delivery
 *   <li>Badge/achievement notifications
 * </ul>
 */
@Controller
public class NotificationWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

  private final SimpMessagingTemplate messagingTemplate;
  private final UserSessionManager userSessionManager;

  public NotificationWebSocketHandler(
      SimpMessagingTemplate messagingTemplate,
      UserSessionManager userSessionManager) {
    this.messagingTemplate = messagingTemplate;
    this.userSessionManager = userSessionManager;
  }

  /**
   * Handle subscribe to community updates.
   *
   * @param communityId the community ID to subscribe to
   * @param headerAccessor the message header accessor
   * @return response indicating success or failure
   */
  @MessageMapping(WebSocketConstants.APP_NOTIFICATION_SUBSCRIBE + "/{communityId}")
  @SendToUser(WebSocketConstants.USER_QUEUE_NOTIFICATION)
  public SubscriptionResponse handleSubscribeToCommunity(
      @DestinationVariable String communityId, SimpMessageHeaderAccessor headerAccessor) {

    SocketClientData userData = getUserData(headerAccessor);

    if (userData == null) {
      return SubscriptionResponse.error("UNAUTHORIZED", "You must be authenticated");
    }

    // Subscribe to community updates
    userSessionManager.subscribeToCommunity(userData.userId(), communityId);

    log.debug("User {} subscribed to community {}", userData.username(), communityId);

    return SubscriptionResponse.success("community", communityId, "Successfully subscribed to community");
  }

  /**
   * Handle exceptions from message handling.
   *
   * @param e the exception
   * @return error response
   */
  @MessageExceptionHandler
  @SendToUser(WebSocketConstants.USER_QUEUE_ERRORS)
  public WebSocketErrorMessage handleException(Exception e) {
    log.error("Notification WebSocket error: {}", e.getMessage(), e);
    return WebSocketErrorMessage.of("INTERNAL_ERROR", e.getMessage());
  }

  /**
   * Get user data from session attributes.
   *
   * @param headerAccessor the message header accessor
   * @return the user data or null if not authenticated
   */
  private SocketClientData getUserData(SimpMessageHeaderAccessor headerAccessor) {
    Map<String, Object> attrs = headerAccessor.getSessionAttributes();
    if (attrs == null) {
      return null;
    }
    Object user = attrs.get("user");
    return user instanceof SocketClientData data ? data : null;
  }

  /** Subscription response record. */
  public record SubscriptionResponse(
      boolean success, String type, String targetId, String message, String error) {

    /**
     * Create a success response.
     *
     * @param type the subscription type
     * @param targetId the target ID
     * @param message the success message
     * @return success response
     */
    public static SubscriptionResponse success(String type, String targetId, String message) {
      return new SubscriptionResponse(true, type, targetId, message, null);
    }

    /**
     * Create an error response.
     *
     * @param error the error code
     * @param message the error message
     * @return error response
     */
    public static SubscriptionResponse error(String error, String message) {
      return new SubscriptionResponse(false, null, null, message, error);
    }
  }
}

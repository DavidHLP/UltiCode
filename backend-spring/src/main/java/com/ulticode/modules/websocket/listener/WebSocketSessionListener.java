package com.ulticode.modules.websocket.listener;

import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.notification.UserSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * WebSocket event listener for tracking user sessions.
 *
 * <p>Registers and unregisters user sessions with UserSessionManager to enable targeted messaging
 * via @SendToUser.
 */
@Component
public class WebSocketSessionListener {

  private static final Logger log = LoggerFactory.getLogger(WebSocketSessionListener.class);

  private final UserSessionManager userSessionManager;

  public WebSocketSessionListener(UserSessionManager userSessionManager) {
    this.userSessionManager = userSessionManager;
  }

  @EventListener
  public void handleSessionConnected(SessionConnectedEvent event) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);

    if (accessor == null) {
      log.warn("SessionConnectedEvent with null accessor");
      return;
    }

    accessor.setLeaveMutable(true);
    String sessionId = accessor.getSessionId();

    log.info("=== SessionConnectedEvent FIRED - this means STOMP CONNECTED was sent to client ===");
    log.info("SessionConnectedEvent: sessionId={}, user={}, sessionAttributes={}",
        sessionId, accessor.getUser(), accessor.getSessionAttributes());

    String userId = getUserId(accessor);
    if (userId != null) {
      userSessionManager.registerSession(userId, sessionId);
      log.info("User session registered in SessionConnectedEvent: userId={}, sessionId={}", userId, sessionId);
    } else {
      log.warn("SessionConnectedEvent without user: sessionId={}", sessionId);
    }
  }

  @EventListener
  public void handleWebSocketConnectListener(SessionConnectEvent event) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);

    if (accessor == null) {
      log.warn("SessionConnectEvent with null accessor");
      return;
    }

    accessor.setLeaveMutable(true);
    String sessionId = accessor.getSessionId();

    log.info("SessionConnectEvent (STOMP CONNECT received): sessionId={}, user={}",
        sessionId, accessor.getUser());
  }

  @EventListener
  public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    String sessionId = event.getSessionId();

    log.info("SessionDisconnectEvent: sessionId={}", sessionId);

    userSessionManager.unregisterSession(sessionId);
    log.info("User session unregistered: sessionId={}", sessionId);
  }

  @EventListener
  public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);

    if (accessor == null) {
      return;
    }

    String sessionId = accessor.getSessionId();
    String destination = accessor.getDestination();
    String userId = getUserId(accessor);

    log.info("SessionSubscribeEvent: sessionId={}, destination={}, userId={}",
        sessionId, destination, userId);
  }

  private String getUserId(StompHeaderAccessor accessor) {
    // First check session attributes (set by JwtChannelInterceptor)
    if (accessor.getSessionAttributes() != null) {
      Object userObj = accessor.getSessionAttributes().get("user");
      if (userObj instanceof SocketClientData userData) {
        return userData.userId();
      }
    }

    // Fall back to user principal
    if (accessor.getUser() != null) {
      return accessor.getUser().getName();
    }

    return null;
  }
}
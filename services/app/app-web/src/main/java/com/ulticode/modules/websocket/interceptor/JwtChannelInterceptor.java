package com.ulticode.modules.websocket.interceptor;

import com.ulticode.app.error.WebSocketErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.websocket.auth.WebSocketAuthenticator;
import com.ulticode.modules.websocket.dto.SocketClientData;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP adapter for WebSocket connection authentication.
 *
 * <p>This class owns the transport only: it speaks STOMP, extracts the token
 * candidate from handshake-populated session attributes, calls the deep
 * {@link WebSocketAuthenticator}, and attaches the resulting principal to
 * the session. The authentication policy (blacklist, signature, expiry,
 * payload sanity, user existence) lives in the authenticator.
 *
 * @author ulticode
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

  private static final Logger log = LoggerFactory.getLogger(JwtChannelInterceptor.class);

  private final WebSocketAuthenticator authenticator;

  public JwtChannelInterceptor(WebSocketAuthenticator authenticator) {
    this.authenticator = authenticator;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) {
      log.warn("WebSocket message with null StompHeaderAccessor");
      return message;
    }

    StompCommand command = accessor.getCommand();
    log.debug("WebSocket STOMP command: {}, sessionId: {}", command, accessor.getSessionId());

    try {
      if (StompCommand.CONNECT.equals(command)) {
        return handleConnect(accessor, message, channel);
      } else if (StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command)) {
        validateUserSession(accessor, command);
      }
    } catch (WebSocketAuthenticationException e) {
      log.error("WebSocket auth error for session {}: {} - {}",
          accessor.getSessionId(), e.getErrorCode(), e.getMessage());
      throw e;
    } catch (ExpiredJwtException e) {
      log.error("WebSocket JWT expired for session {}: {}", accessor.getSessionId(), e.getMessage());
      throw e;
    } catch (MalformedJwtException | SignatureException | UnsupportedJwtException | IllegalArgumentException e) {
      log.error("WebSocket invalid JWT for session {}: {}", accessor.getSessionId(), e.getMessage());
      throw e;
    }

    return message;
  }

  private Message<?> handleConnect(StompHeaderAccessor accessor, Message<?> message, MessageChannel channel) {
    log.debug("=== Handling CONNECT for sessionId: {} ===", accessor.getSessionId());
    log.debug("Session attributes BEFORE auth: {}", accessor.getSessionAttributes());

    try {
      authenticateConnection(accessor);
      log.debug("=== CONNECT authenticated successfully for sessionId: {} ===", accessor.getSessionId());
      log.debug("Session attributes AFTER auth: {}", accessor.getSessionAttributes());
      log.debug("User principal AFTER auth: {}", accessor.getUser());
    } catch (WebSocketAuthenticationException e) {
      log.error("=== CONNECT auth FAILED for sessionId: {}: {} ===",
          accessor.getSessionId(), e.getErrorCode());
      throw e;
    } catch (ExpiredJwtException e) {
      log.error("=== JWT expired in CONNECT for sessionId: {}: {} ===",
          accessor.getSessionId(), e.getMessage());
      throw e;
    } catch (MalformedJwtException | SignatureException | UnsupportedJwtException | IllegalArgumentException e) {
      log.error("=== Invalid JWT in CONNECT for sessionId: {}: {} ===",
          accessor.getSessionId(), e.getMessage());
      throw e;
    }

    return message;
  }

  private void validateUserSession(StompHeaderAccessor accessor, StompCommand command) {
    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes == null) {
      // Phase 0 / PROJECT_DOCUMENTATION.md §7.1: SEND/SUBSCRIBE
      // must fail closed when the session has no attributes at all. The
      // pre-fix behaviour was log-and-return, which silently admitted
      // frames on unattributed sessions — that is a fail-open bug.
      throw new WebSocketAuthenticationException(
          WebSocketErrorCode.SESSION_MISSING,
          "WebSocket session is missing attributes for " + command);
    }

    Object userObj = sessionAttributes.get("user");
    if (userObj == null) {
      // Same rationale: a SEND/SUBSCRIBE without a bound principal must
      // not silently pass through.
      throw new WebSocketAuthenticationException(
          WebSocketErrorCode.SESSION_MISSING,
          "WebSocket session has no authenticated user for " + command);
    }

    if (!(userObj instanceof SocketClientData)) {
      // Garbage in the session map (regression / manual injection) must
      // not become a silent bypass either.
      throw new WebSocketAuthenticationException(
          WebSocketErrorCode.SESSION_MISSING,
          "WebSocket session has invalid user payload for " + command);
    }

    SocketClientData userData = (SocketClientData) userObj;
    log.debug("WebSocket {} command for user: {}, destination: {}",
        command, userData.username(), accessor.getDestination());
  }

  private void authenticateConnection(StompHeaderAccessor accessor) {
    log.debug("Authenticating WebSocket CONNECT, sessionId: {}, sessionAttributes: {}",
        accessor.getSessionId(), accessor.getSessionAttributes());

    // The handshake interceptor is the only token source; client-controlled
    // STOMP headers must never authenticate a connection.
    Optional<String> tokenOpt = extractTokenFromSession(accessor);

    SocketClientData clientData = authenticator.authenticate(tokenOpt);

    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes != null) {
      sessionAttributes.put("user", clientData);
      log.debug("User data added to session, sessionId: {}", accessor.getSessionId());
    } else {
      log.warn("Session attributes is null during authentication, sessionId: {}",
          accessor.getSessionId());
    }

    // Set user principal for @MessageMapping methods
    accessor.setUser(clientData::userId);

    log.info("WebSocket authenticated: userId={}, username={}, sessionId={}, userPrincipal={}",
        clientData.userId(), clientData.username(),
        accessor.getSessionId(), accessor.getUser());
  }

  private Optional<String> extractTokenFromSession(StompHeaderAccessor accessor) {
    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes != null) {
      Object auth = sessionAttributes.get("auth");
      if (auth instanceof String token && !token.isEmpty()) {
        return Optional.of(token);
      }
    }
    return Optional.empty();
  }

  /** Exception for WebSocket authentication failures. */
  public static class WebSocketAuthenticationException extends RuntimeException {
    private final com.ulticode.common.error.NamespacedErrorCode errorCode;

    public WebSocketAuthenticationException(com.ulticode.common.error.NamespacedErrorCode errorCode, String message) {
      super(message);
      this.errorCode = errorCode;
    }

    public com.ulticode.common.error.NamespacedErrorCode getErrorCode() {
      return errorCode;
    }
  }
}

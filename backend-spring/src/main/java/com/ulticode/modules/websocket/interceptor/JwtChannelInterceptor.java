package com.ulticode.modules.websocket.interceptor;

import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.util.JwtUtils;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.port.TokenBlacklistPort;
import com.ulticode.modules.websocket.util.TokenExtractor;
import io.jsonwebtoken.Claims;
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
 * JWT Channel interceptor for authenticating WebSocket connections.
 *
 * <p>Validates JWT tokens and checks token blacklist before allowing STOMP commands.
 * This interceptor is applied to all WebSocket channels to ensure secure connections.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

  private static final Logger log = LoggerFactory.getLogger(JwtChannelInterceptor.class);

  private final JwtUtils jwtUtils;
  private final TokenBlacklistPort tokenBlacklistPort;
  private final UserReadProjection userReadProjection;
  private final TokenExtractor tokenExtractor;

  public JwtChannelInterceptor(
      JwtUtils jwtUtils,
      TokenBlacklistPort tokenBlacklistPort,
      UserReadProjection userReadProjection,
      TokenExtractor tokenExtractor) {
    this.jwtUtils = jwtUtils;
    this.tokenBlacklistPort = tokenBlacklistPort;
    this.userReadProjection = userReadProjection;
    this.tokenExtractor = tokenExtractor;
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

  /**
   * Handle STOMP CONNECT command with proper exception handling.
   */
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

  /**
   * Validate that user session exists for message processing.
   *
   * @param accessor the STOMP header accessor
   * @param command the STOMP command being processed
   */
  private void validateUserSession(StompHeaderAccessor accessor, StompCommand command) {
    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes == null) {
      log.warn("WebSocket {} command with null session attributes, destination: {}",
          command, accessor.getDestination());
      return;
    }

    Object userObj = sessionAttributes.get("user");
    if (userObj == null) {
      log.warn("WebSocket {} command with no user in session, destination: {}, sessionId: {}",
          command, accessor.getDestination(), accessor.getSessionId());
      return;
    }

    if (!(userObj instanceof SocketClientData)) {
      log.warn("WebSocket {} command with invalid user type: {}, destination: {}",
          command, userObj.getClass().getName(), accessor.getDestination());
      return;
    }

    SocketClientData userData = (SocketClientData) userObj;
    log.debug("WebSocket {} command for user: {}, destination: {}",
        command, userData.username(), accessor.getDestination());
  }

  /**
   * Authenticate a STOMP CONNECT command.
   *
   * @param accessor the STOMP header accessor
   */
  private void authenticateConnection(StompHeaderAccessor accessor) {
    log.debug("Authenticating WebSocket CONNECT, sessionId: {}, sessionAttributes: {}",
        accessor.getSessionId(), accessor.getSessionAttributes());

    // First try session attributes (set by HandshakeInterceptor from query param/cookie)
    Optional<String> tokenOpt = extractTokenFromSession(accessor);

    // Fall back to STOMP CONNECT message headers
    if (tokenOpt.isEmpty()) {
      tokenOpt = tokenExtractor.extractTokenFromHeaders(accessor.getMessageHeaders());
    }

    if (tokenOpt.isEmpty()) {
      log.warn("WebSocket connection rejected: No token provided");
      throw new WebSocketAuthenticationException(
          ErrorCode.WEBSOCKET_UNAUTHORIZED, "No authentication token provided");
    }

    String token = tokenOpt.get();

    // Check if token is blacklisted
    if (tokenBlacklistPort.isBlacklisted(token)) {
      log.warn("WebSocket connection rejected: Token is blacklisted");
      throw new WebSocketAuthenticationException(
          ErrorCode.WEBSOCKET_TOKEN_BLACKLISTED, "Token has been revoked");
    }

    // Validate token
    Optional<Claims> claimsOpt = jwtUtils.validateToken(token);
    if (claimsOpt.isEmpty()) {
      log.warn("WebSocket connection rejected: Invalid token");
      throw new WebSocketAuthenticationException(
          ErrorCode.WEBSOCKET_INVALID_TOKEN, "Invalid or expired token");
    }

    Claims claims = claimsOpt.get();
    String userId = claims.getSubject();

    if (userId == null || userId.isEmpty()) {
      log.warn("WebSocket connection rejected: Invalid token payload");
      throw new WebSocketAuthenticationException(
          ErrorCode.WEBSOCKET_INVALID_TOKEN, "Invalid token payload");
    }

    // Verify user exists
    Optional<User> userOpt = userReadProjection.findById(userId);
    if (userOpt.isEmpty()) {
      log.warn("WebSocket connection rejected: User not found, userId: {}", userId);
      throw new WebSocketAuthenticationException(
          ErrorCode.WEBSOCKET_USER_NOT_FOUND, "User not found");
    }

    User user = userOpt.get();

    // Attach user data to session
    SocketClientData clientData =
        new SocketClientData(userId, user.getUsername(), user.getRole());

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
        userId, user.getUsername(), accessor.getSessionId(), accessor.getUser());
  }

  /**
   * Extract token from WebSocket session attributes.
   *
   * @param accessor the STOMP header accessor
   * @return Optional containing the token if found in session attributes
   */
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
    private final ErrorCode errorCode;

    public WebSocketAuthenticationException(ErrorCode errorCode, String message) {
      super(message);
      this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
      return errorCode;
    }
  }
}

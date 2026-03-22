package com.ulticode.modules.websocket.interceptor;

import com.ulticode.common.constants.ErrorCode;
import com.ulticode.common.service.TokenBlacklistService;
import com.ulticode.modules.auth.util.JwtUtils;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.util.TokenExtractor;
import io.jsonwebtoken.Claims;
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
  private final TokenBlacklistService tokenBlacklistService;
  private final UserService userService;
  private final TokenExtractor tokenExtractor;

  public JwtChannelInterceptor(
      JwtUtils jwtUtils,
      TokenBlacklistService tokenBlacklistService,
      UserService userService,
      TokenExtractor tokenExtractor) {
    this.jwtUtils = jwtUtils;
    this.tokenBlacklistService = tokenBlacklistService;
    this.userService = userService;
    this.tokenExtractor = tokenExtractor;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) {
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      authenticateConnection(accessor);
    }

    return message;
  }

  /**
   * Authenticate a STOMP CONNECT command.
   *
   * @param accessor the STOMP header accessor
   */
  private void authenticateConnection(StompHeaderAccessor accessor) {
    // Extract token from headers
    Optional<String> tokenOpt =
        tokenExtractor.extractTokenFromHeaders(accessor.getMessageHeaders());

    if (tokenOpt.isEmpty()) {
      log.warn("WebSocket connection rejected: No token provided");
      throw new WebSocketAuthenticationException(
          ErrorCode.WEBSOCKET_UNAUTHORIZED, "No authentication token provided");
    }

    String token = tokenOpt.get();

    // Check if token is blacklisted
    if (tokenBlacklistService.isTokenBlacklisted(token)) {
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
    Optional<User> userOpt = userService.findById(userId);
    if (userOpt.isEmpty()) {
      log.warn("WebSocket connection rejected: User not found");
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
    }

    // Set user principal for @MessageMapping methods
    accessor.setUser(clientData::userId);

    log.debug("WebSocket authenticated: userId={}, username={}", userId, user.getUsername());
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

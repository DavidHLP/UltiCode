package com.ulticode.modules.websocket.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.util.JwtUtils;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.port.TokenBlacklistPort;
import com.ulticode.modules.websocket.util.TokenExtractor;
import io.jsonwebtoken.Claims;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/** Tests for JwtChannelInterceptor. */
@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

  @Mock private JwtUtils jwtUtils;
  @Mock private TokenBlacklistPort tokenBlacklistPort;
  @Mock private UserService userService;
  @Mock private TokenExtractor tokenExtractor;
  @Mock private MessageChannel channel;

  private JwtChannelInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new JwtChannelInterceptor(jwtUtils, tokenBlacklistPort, userService, tokenExtractor);
  }

  @Test
  void preSend_withNonStompMessage_returnsMessage() {
    Message<?> message = MessageBuilder.withPayload("test").build();

    Message<?> result = interceptor.preSend(message, channel);

    assertSame(message, result);
  }

  @Test
  void preSend_withNonConnectCommand_returnsMessage() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setDestination("/app/test");
    Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);

    assertSame(message, result);
  }

  @Test
  void preSend_withValidToken_authenticatesSuccessfully() {
    String token = "valid-token";
    String userId = "user-123";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    // Keep the accessor mutable - don't call getMessageHeaders() until we need to
    accessor.setLeaveMutable(true);

    Claims claims = mock(Claims.class);
    User user = createUser(userId, "testuser", "USER");

    when(tokenExtractor.extractTokenFromHeaders(any())).thenReturn(Optional.of(token));
    when(tokenBlacklistPort.isBlacklisted(token)).thenReturn(false);
    when(jwtUtils.validateToken(token)).thenReturn(Optional.of(claims));
    when(claims.getSubject()).thenReturn(userId);
    when(userService.findById(userId)).thenReturn(Optional.of(user));

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
    Message<?> result = interceptor.preSend(message, channel);

    assertSame(message, result);
    verify(tokenExtractor).extractTokenFromHeaders(any());
    verify(tokenBlacklistPort).isBlacklisted(token);
    verify(jwtUtils).validateToken(token);
    verify(userService).findById(userId);
  }

  @Test
  void preSend_withoutToken_throwsException() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Map<String, Object> headers = new HashMap<>(accessor.getMessageHeaders());

    when(tokenExtractor.extractTokenFromHeaders(headers)).thenReturn(Optional.empty());

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        assertThrows(
            JwtChannelInterceptor.WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));

    assertEquals(ErrorCode.WEBSOCKET_UNAUTHORIZED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("No authentication token"));
  }

  @Test
  void preSend_withBlacklistedToken_throwsException() {
    String token = "blacklisted-token";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Map<String, Object> headers = new HashMap<>(accessor.getMessageHeaders());

    when(tokenExtractor.extractTokenFromHeaders(headers)).thenReturn(Optional.of(token));
    when(tokenBlacklistPort.isBlacklisted(token)).thenReturn(true);

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        assertThrows(
            JwtChannelInterceptor.WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));

    assertEquals(ErrorCode.WEBSOCKET_TOKEN_BLACKLISTED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("revoked"));
  }

  @Test
  void preSend_withInvalidToken_throwsException() {
    String token = "invalid-token";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Map<String, Object> headers = new HashMap<>(accessor.getMessageHeaders());

    when(tokenExtractor.extractTokenFromHeaders(headers)).thenReturn(Optional.of(token));
    when(tokenBlacklistPort.isBlacklisted(token)).thenReturn(false);
    when(jwtUtils.validateToken(token)).thenReturn(Optional.empty());

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        assertThrows(
            JwtChannelInterceptor.WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));

    assertEquals(ErrorCode.WEBSOCKET_INVALID_TOKEN, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Invalid or expired"));
  }

  @Test
  void preSend_withNullUserIdInToken_throwsException() {
    String token = "token-without-userId";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Map<String, Object> headers = new HashMap<>(accessor.getMessageHeaders());

    Claims claims = mock(Claims.class);

    when(tokenExtractor.extractTokenFromHeaders(headers)).thenReturn(Optional.of(token));
    when(tokenBlacklistPort.isBlacklisted(token)).thenReturn(false);
    when(jwtUtils.validateToken(token)).thenReturn(Optional.of(claims));
    when(claims.getSubject()).thenReturn(null);

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        assertThrows(
            JwtChannelInterceptor.WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));

    assertEquals(ErrorCode.WEBSOCKET_INVALID_TOKEN, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("Invalid token payload"));
  }

  @Test
  void preSend_withEmptyUserIdInToken_throwsException() {
    String token = "token-with-empty-userId";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Map<String, Object> headers = new HashMap<>(accessor.getMessageHeaders());

    Claims claims = mock(Claims.class);

    when(tokenExtractor.extractTokenFromHeaders(headers)).thenReturn(Optional.of(token));
    when(tokenBlacklistPort.isBlacklisted(token)).thenReturn(false);
    when(jwtUtils.validateToken(token)).thenReturn(Optional.of(claims));
    when(claims.getSubject()).thenReturn("");

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        assertThrows(
            JwtChannelInterceptor.WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));

    assertEquals(ErrorCode.WEBSOCKET_INVALID_TOKEN, exception.getErrorCode());
  }

  @Test
  void preSend_withUserNotFound_throwsException() {
    String token = "valid-token";
    String userId = "nonexistent-user";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Map<String, Object> headers = new HashMap<>(accessor.getMessageHeaders());

    Claims claims = mock(Claims.class);

    when(tokenExtractor.extractTokenFromHeaders(headers)).thenReturn(Optional.of(token));
    when(tokenBlacklistPort.isBlacklisted(token)).thenReturn(false);
    when(jwtUtils.validateToken(token)).thenReturn(Optional.of(claims));
    when(claims.getSubject()).thenReturn(userId);
    when(userService.findById(userId)).thenReturn(Optional.empty());

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        assertThrows(
            JwtChannelInterceptor.WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));

    assertEquals(ErrorCode.WEBSOCKET_USER_NOT_FOUND, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("User not found"));
  }

  @Test
  void webSocketAuthenticationException_getErrorCode_returnsCode() {
    ErrorCode errorCode = ErrorCode.WEBSOCKET_UNAUTHORIZED;
    String message = "Test error message";

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        new JwtChannelInterceptor.WebSocketAuthenticationException(errorCode, message);

    assertEquals(errorCode, exception.getErrorCode());
    assertEquals(message, exception.getMessage());
  }

  private User createUser(String id, String username, String role) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setRole(role);
    return user;
  }
}

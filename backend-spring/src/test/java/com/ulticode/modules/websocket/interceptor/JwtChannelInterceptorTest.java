package com.ulticode.modules.websocket.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ulticode.modules.websocket.auth.WebSocketAuthenticator;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import com.ulticode.modules.websocket.util.TokenExtractor;
import com.ulticode.common.exception.ErrorCode;
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

/**
 * Tests for the STOMP transport adapter.
 *
 * <p>The transport now only knows how to extract a token candidate and
 * delegate to the {@link WebSocketAuthenticator}. Auth policy tests
 * (blacklist, expiry, user existence, fail-closed) live in
 * {@code DefaultWebSocketAuthenticatorTest}.
 */
@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

  @Mock private WebSocketAuthenticator authenticator;
  @Mock private TokenExtractor tokenExtractor;
  @Mock private MessageChannel channel;

  private JwtChannelInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new JwtChannelInterceptor(authenticator, tokenExtractor);
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
    verifyNoInteractions(authenticator);
  }

  @Test
  void preSend_withValidToken_delegatesAndAttachesPrincipal() {
    String token = "valid-token";
    String userId = "user-123";
    SocketClientData clientData = new SocketClientData(userId, "testuser", "USER");

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    Map<String, Object> sessionAttrs = new HashMap<>();
    accessor.setSessionAttributes(sessionAttrs);

    when(tokenExtractor.extractTokenFromHeaders(any())).thenReturn(Optional.of(token));
    when(authenticator.authenticate(Optional.of(token))).thenReturn(clientData);

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
    Message<?> result = interceptor.preSend(message, channel);

    assertSame(message, result);
    verify(authenticator).authenticate(Optional.of(token));
    assertEquals(clientData, accessor.getSessionAttributes().get("user"));
    assertNotNull(accessor.getUser());
  }

  @Test
  void preSend_propagatesAuthenticatorException() {
    String token = "bad-token";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    accessor.setSessionAttributes(new HashMap<>());

    when(tokenExtractor.extractTokenFromHeaders(any())).thenReturn(Optional.of(token));
    when(authenticator.authenticate(any()))
            .thenThrow(new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_TOKEN_BLACKLISTED, "Token has been revoked"));

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    WebSocketAuthenticationException ex = assertThrows(
            WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));
    assertEquals(ErrorCode.WEBSOCKET_TOKEN_BLACKLISTED, ex.getErrorCode());
  }

  @Test
  void preSend_withoutToken_delegatesEmptyOptional() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    accessor.setSessionAttributes(new HashMap<>());

    when(tokenExtractor.extractTokenFromHeaders(any())).thenReturn(Optional.empty());
    when(authenticator.authenticate(Optional.empty()))
            .thenThrow(new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_UNAUTHORIZED, "No authentication token provided"));

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    WebSocketAuthenticationException ex = assertThrows(
            WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));
    assertEquals(ErrorCode.WEBSOCKET_UNAUTHORIZED, ex.getErrorCode());
    verify(authenticator).authenticate(Optional.empty());
  }

  @Test
  void preSend_withSendCommand_doesNotInvokeAuthenticator() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setDestination("/app/test");
    Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);

    assertSame(message, result);
    verifyNoInteractions(authenticator);
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

  @Test
  void preSend_prefersSessionTokenOverHeaderToken() {
    String sessionToken = "session-token";
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    Map<String, Object> sessionAttrs = new HashMap<>();
    sessionAttrs.put("auth", sessionToken);
    accessor.setSessionAttributes(sessionAttrs);

    when(authenticator.authenticate(Optional.of(sessionToken)))
            .thenReturn(new SocketClientData("u-1", "alice", "USER"));

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
    interceptor.preSend(message, channel);

    verify(authenticator).authenticate(Optional.of(sessionToken));
    verifyNoInteractions(tokenExtractor);
  }
}
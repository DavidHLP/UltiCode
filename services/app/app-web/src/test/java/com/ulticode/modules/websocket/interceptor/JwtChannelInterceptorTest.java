package com.ulticode.modules.websocket.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ulticode.modules.websocket.auth.WebSocketAuthenticator;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import com.ulticode.app.error.WebSocketErrorCode;
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
 *
 * <p>Phase 0 (PROJECT_DOCUMENTATION.md §7.1) added fail-closed
 * behavior for SEND/SUBSCRIBE without a bound principal: pre-fix these
 * commands logged-and-returned, silently admitting frames on
 * unattributed sessions. New tests assert the WebSocketAuthentication
 * throw path.
 */
@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

  @Mock private WebSocketAuthenticator authenticator;
  @Mock private MessageChannel channel;

  private JwtChannelInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new JwtChannelInterceptor(authenticator);
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
    // Phase 0 §7.1: SEND/SUBSCRIBE now fail closed when the session has
    // no bound principal. Bind a synthetic principal so the frame passes
    // validateUserSession without exercising the new error branch.
    Map<String, Object> sessionAttrs = new HashMap<>();
    sessionAttrs.put("user", new SocketClientData("u-test", "tester", "USER"));
    accessor.setSessionAttributes(sessionAttrs);
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

    sessionAttrs.put("auth", token);
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
    Map<String, Object> sessionAttrs = new HashMap<>();
    accessor.setSessionAttributes(sessionAttrs);

    sessionAttrs.put("auth", token);
    when(authenticator.authenticate(any()))
            .thenThrow(new WebSocketAuthenticationException(
                    WebSocketErrorCode.TOKEN_BLACKLISTED, "Token has been revoked"));

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    WebSocketAuthenticationException ex = assertThrows(
            WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));
    assertEquals(WebSocketErrorCode.TOKEN_BLACKLISTED, ex.getErrorCode());
  }

  @Test
  void preSend_withoutToken_delegatesEmptyOptional() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    accessor.setSessionAttributes(new HashMap<>());

    when(authenticator.authenticate(Optional.empty()))
            .thenThrow(new WebSocketAuthenticationException(
                    WebSocketErrorCode.UNAUTHORIZED, "No authentication token provided"));

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    WebSocketAuthenticationException ex = assertThrows(
            WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));
    assertEquals(WebSocketErrorCode.UNAUTHORIZED, ex.getErrorCode());
    verify(authenticator).authenticate(Optional.empty());
  }

  @Test
  void preSend_withSendCommand_doesNotInvokeAuthenticator() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setDestination("/app/test");
    Map<String, Object> sessionAttrs = new HashMap<>();
    sessionAttrs.put("user", new SocketClientData("u-test", "tester", "USER"));
    accessor.setSessionAttributes(sessionAttrs);
    Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, channel);

    assertSame(message, result);
    verifyNoInteractions(authenticator);
  }

  @Test
  void webSocketAuthenticationException_getErrorCode_returnsCode() {
    var errorCode = WebSocketErrorCode.UNAUTHORIZED;
    String message = "Test error message";

    JwtChannelInterceptor.WebSocketAuthenticationException exception =
        new JwtChannelInterceptor.WebSocketAuthenticationException(errorCode, message);

    assertEquals(errorCode, exception.getErrorCode());
    assertEquals(message, exception.getMessage());
  }

  @Test
  void preSend_usesSessionTokenFromHandshakeCookie() {
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
  }

  @Test
  void preSend_withHeaderTokenAndNoSessionToken_rejectsHeaderAuthentication() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setLeaveMutable(true);
    accessor.setNativeHeader("auth", "header-token");
    accessor.setSessionAttributes(new HashMap<>());

    when(authenticator.authenticate(Optional.empty()))
            .thenThrow(new WebSocketAuthenticationException(
                    WebSocketErrorCode.UNAUTHORIZED, "No authentication token provided"));

    Message<?> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

    WebSocketAuthenticationException ex = assertThrows(
            WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));

    assertEquals(WebSocketErrorCode.UNAUTHORIZED, ex.getErrorCode());
    verify(authenticator).authenticate(Optional.empty());
  }

  // ============ Phase 0 §7.1: SEND/SUBSCRIBE fail-closed ============

  @Test
  void preSend_sendWithoutSessionAttributes_throwsSessionMissing() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
    accessor.setDestination("/app/test");
    // No session attributes bound — pre-fix this passed silently.
    Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

    WebSocketAuthenticationException ex = assertThrows(
            WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));
    assertEquals(WebSocketErrorCode.SESSION_MISSING, ex.getErrorCode());
    verifyNoInteractions(authenticator);
  }

  @Test
  void preSend_subscribeWithoutUserInSession_throwsSessionMissing() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination("/topic/foo");
    Map<String, Object> sessionAttrs = new HashMap<>();
    // Session attributes present but "user" missing.
    accessor.setSessionAttributes(sessionAttrs);
    Message<?> message = MessageBuilder.createMessage("test", accessor.getMessageHeaders());

    WebSocketAuthenticationException ex = assertThrows(
            WebSocketAuthenticationException.class,
            () -> interceptor.preSend(message, channel));
    assertEquals(WebSocketErrorCode.SESSION_MISSING, ex.getErrorCode());
    verifyNoInteractions(authenticator);
  }
}

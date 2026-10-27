package com.ulticode.modules.websocket.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ulticode.modules.websocket.util.TokenExtractor;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class WebSocketAuthHandshakeInterceptorTest {
  @Test
  void storesOnlyAnOpaqueCookieCredential() {
    TokenExtractor extractor = mock(TokenExtractor.class);
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getURI()).thenReturn(URI.create("http://localhost/ws"));
    when(extractor.extractToken(request)).thenReturn(Optional.of("private-cookie-token"));
    var attributes = new HashMap<String, Object>();

    assertTrue(new WebSocketAuthHandshakeInterceptor(extractor).beforeHandshake(
        request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes));
    assertInstanceOf(WebSocketAuthHandshakeInterceptor.SessionCredential.class, attributes.get("auth"));
    assertFalse(attributes.toString().contains("private-cookie-token"));
    assertTrue(attributes.toString().contains("[REDACTED]"));
  }

  @Test
  void missingCookieStillRejectsHandshake() {
    TokenExtractor extractor = mock(TokenExtractor.class);
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getURI()).thenReturn(URI.create("http://localhost/ws"));
    when(extractor.extractToken(request)).thenReturn(Optional.empty());
    var attributes = new HashMap<String, Object>();
    assertFalse(new WebSocketAuthHandshakeInterceptor(extractor).beforeHandshake(
        request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes));
    assertTrue(attributes.isEmpty());
  }
}

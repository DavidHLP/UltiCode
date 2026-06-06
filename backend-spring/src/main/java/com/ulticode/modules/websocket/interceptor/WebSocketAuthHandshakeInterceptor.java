package com.ulticode.modules.websocket.interceptor;

import com.ulticode.modules.websocket.util.TokenExtractor;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Handshake interceptor that extracts JWT token from the HTTP request and stores it in the
 * WebSocket session attributes.
 *
 * <p>This is necessary because SockJS does not forward custom headers (like Authorization) during
 * the WebSocket handshake. The token is extracted only from the HttpOnly access token cookie.
 *
 * <p>The token is then available via session attributes for the JwtChannelInterceptor to use during
 * STOMP CONNECT authentication.
 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

  private static final Logger log =
      LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);

  private final TokenExtractor tokenExtractor;

  public WebSocketAuthHandshakeInterceptor(TokenExtractor tokenExtractor) {
    this.tokenExtractor = tokenExtractor;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {

    log.debug("WebSocket handshake started: path={}, remote={}",
        request.getURI().getPath(), request.getRemoteAddress());

    var tokenOpt = tokenExtractor.extractToken(request);
    if (tokenOpt.isPresent()) {
      attributes.put("auth", tokenOpt.get());
    } else {
      log.warn("WebSocket handshake rejected: no authentication token found");
      return false;
    }
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // No post-handshake actions needed
  }
}

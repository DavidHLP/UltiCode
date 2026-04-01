package com.ulticode.modules.websocket.interceptor;

import com.ulticode.modules.websocket.util.TokenExtractor;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Handshake interceptor that extracts JWT token from the HTTP request and stores it in the
 * WebSocket session attributes.
 *
 * <p>This is necessary because SockJS does not forward custom headers (like Authorization) during
 * the WebSocket handshake. The token is extracted from: 1. Query parameter (?token=xxx) 2. Cookie
 * (access_token) 3. Authorization header (Bearer xxx)
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

    log.info("=== WebSocket handshake STARTED ===");
    log.info("Request URI: {}, Remote address: {}", request.getURI(), request.getRemoteAddress());

    // Extract token from HTTP request (query params, cookies, headers)
    var tokenOpt = tokenExtractor.extractToken(request);
    if (tokenOpt.isPresent()) {
      attributes.put("auth", tokenOpt.get());
      log.info("Token extracted during handshake, token starts with: {}", 
          tokenOpt.get().substring(0, Math.min(20, tokenOpt.get().length())));
    } else {
      log.warn("No token found during handshake!");
    }

    // Copy all query parameters to session attributes for downstream access
    if (request instanceof ServletServerHttpRequest servletRequest) {
      servletRequest.getServletRequest().getParameterMap().forEach((key, values) -> {
        if (values != null && values.length > 0) {
          attributes.put(key, values[0]);
        }
      });
    }

    log.info("Handshake attributes: {}", attributes.keySet());
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

  /**
   * Extract token from cookie header string.
   *
   * @param cookieHeader the cookie header value
   * @return the access token or null
   */
  private String extractTokenFromCookie(String cookieHeader) {
    if (!StringUtils.hasText(cookieHeader)) {
      return null;
    }

    String[] cookies = cookieHeader.split(";");
    for (String cookie : cookies) {
      String[] parts = cookie.trim().split("=", 2);
      if (parts.length == 2 && "access_token".equals(parts[0].trim())) {
        return parts[1].trim();
      }
    }
    return null;
  }
}

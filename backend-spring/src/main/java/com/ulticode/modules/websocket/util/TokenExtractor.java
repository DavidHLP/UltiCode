package com.ulticode.modules.websocket.util;

import java.util.Map;
import java.util.Optional;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Utility class for extracting JWT tokens from WebSocket requests. */
@Component
public class TokenExtractor {

  /**
   * Extract JWT token from WebSocket handshake request.
   *
   * <p>Tries to extract token from: 1. Auth object in handshake attributes 2. Authorization header
   * 3. Cookie (access_token) 4. Query parameter (token)
   *
   * @param request the server HTTP request
   * @return Optional containing the token if found
   */
  public Optional<String> extractToken(ServerHttpRequest request) {
    // Try auth attribute first (set by handshake interceptor)
    if (request instanceof ServletServerHttpRequest servletRequest) {
      var httpRequest = servletRequest.getServletRequest();

      // Try query parameter
      String queryToken = httpRequest.getParameter("token");
      if (StringUtils.hasText(queryToken)) {
        return Optional.of(queryToken);
      }

      // Try Authorization header
      String authHeader = httpRequest.getHeader("Authorization");
      if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
        return Optional.of(authHeader.substring(7));
      }

      // Try cookie
      String cookie = httpRequest.getHeader("Cookie");
      if (StringUtils.hasText(cookie)) {
        String token = extractTokenFromCookie(cookie);
        if (StringUtils.hasText(token)) {
          return Optional.of(token);
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Extract token from cookie header.
   *
   * @param cookieHeader the cookie header value
   * @return the access token or null
   */
  private String extractTokenFromCookie(String cookieHeader) {
    String[] cookies = cookieHeader.split(";");
    for (String cookie : cookies) {
      String[] parts = cookie.trim().split("=", 2);
      if (parts.length == 2 && "access_token".equals(parts[0])) {
        return parts[1];
      }
    }
    return null;
  }

  /**
   * Extract token from STOMP connect message headers.
   *
   * @param headers the STOMP message headers
   * @return Optional containing the token if found
   */
  @SuppressWarnings("unchecked")
  public Optional<String> extractTokenFromHeaders(Map<String, Object> headers) {
    // Try auth attribute first (set by HandshakeInterceptor from cookie/query param)
    Object auth = headers.get("auth");
    if (auth instanceof String token && StringUtils.hasText(token)) {
      return Optional.of(token);
    }

    // Try native headers (for direct WebSocket connections without SockJS)
    Object nativeHeaders = headers.get("nativeHeaders");
    if (nativeHeaders instanceof Map) {
      Map<String, Object> nh = (Map<String, Object>) nativeHeaders;

      // Try token in native headers
      Object tokenObj = nh.get("token");
      if (tokenObj instanceof java.util.List<?> tokenList && !tokenList.isEmpty()) {
        return Optional.of(String.valueOf(tokenList.get(0)));
      }

      // Try Authorization header
      Object authObj = nh.get("Authorization");
      if (authObj instanceof java.util.List<?> authList && !authList.isEmpty()) {
        String authHeader = String.valueOf(authList.get(0));
        if (authHeader.startsWith("Bearer ")) {
          return Optional.of(authHeader.substring(7));
        }
      }
    }

    return Optional.empty();
  }
}

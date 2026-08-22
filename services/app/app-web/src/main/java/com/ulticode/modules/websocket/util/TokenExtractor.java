package com.ulticode.modules.websocket.util;

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
   * <p>Browser WebSocket authentication is accepted only from the HttpOnly access token cookie.
   *
   * @param request the server HTTP request
   * @return Optional containing the token if found
   */
  public Optional<String> extractToken(ServerHttpRequest request) {
    // Try auth attribute first (set by handshake interceptor)
    if (request instanceof ServletServerHttpRequest servletRequest) {
      var httpRequest = servletRequest.getServletRequest();

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

}

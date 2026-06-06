package com.ulticode.modules.websocket.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;

import jakarta.servlet.http.HttpServletRequest;

/** Tests for TokenExtractor. */
@ExtendWith(MockitoExtension.class)
class TokenExtractorTest {

  private TokenExtractor tokenExtractor;

  @Mock private ServerHttpRequest request;

  @Mock private ServletServerHttpRequest servletRequest;

  @Mock private HttpServletRequest httpRequest;

  @BeforeEach
  void setUp() {
    tokenExtractor = new TokenExtractor();
  }

  // ==================== extractToken tests ====================

  @Test
  void extractToken_fromQueryParam_isRejected() {
    when(servletRequest.getServletRequest()).thenReturn(httpRequest);
    when(httpRequest.getHeader("Cookie")).thenReturn(null);

    Optional<String> result = tokenExtractor.extractToken(servletRequest);

    assertTrue(result.isEmpty());
  }

  @Test
  void extractToken_fromAuthorizationHeader_isRejected() {
    when(servletRequest.getServletRequest()).thenReturn(httpRequest);
    when(httpRequest.getHeader("Cookie")).thenReturn(null);

    Optional<String> result = tokenExtractor.extractToken(servletRequest);

    assertTrue(result.isEmpty());
  }

  @Test
  void extractToken_fromCookie_returnsToken() {
    String expectedToken = "cookie-token";
    String cookieHeader = "other=value; access_token=" + expectedToken + "; another=val";

    when(servletRequest.getServletRequest()).thenReturn(httpRequest);
    when(httpRequest.getHeader("Cookie")).thenReturn(cookieHeader);

    Optional<String> result = tokenExtractor.extractToken(servletRequest);

    assertTrue(result.isPresent());
    assertEquals(expectedToken, result.get());
  }

  @Test
  void extractToken_noTokenInCookie_returnsEmpty() {
    String cookieHeader = "other=value; no_access_token_here=val";

    when(servletRequest.getServletRequest()).thenReturn(httpRequest);
    when(httpRequest.getHeader("Cookie")).thenReturn(cookieHeader);

    Optional<String> result = tokenExtractor.extractToken(servletRequest);

    assertFalse(result.isPresent());
  }

  @Test
  void extractToken_nonServletRequest_returnsEmpty() {
    Optional<String> result = tokenExtractor.extractToken(request);

    assertFalse(result.isPresent());
  }

  @Test
  void extractToken_noTokenSources_returnsEmpty() {
    when(servletRequest.getServletRequest()).thenReturn(httpRequest);
    when(httpRequest.getHeader("Cookie")).thenReturn(null);

    Optional<String> result = tokenExtractor.extractToken(servletRequest);

    assertFalse(result.isPresent());
  }

  // ==================== extractTokenFromHeaders tests ====================

  @Test
  void extractTokenFromHeaders_fromNativeHeadersToken_isRejected() {
    Map<String, Object> headers = new HashMap<>();

    Optional<String> result = tokenExtractor.extractTokenFromHeaders(headers);

    assertTrue(result.isEmpty());
  }

  @Test
  void extractTokenFromHeaders_fromNativeHeadersAuthorization_isRejected() {
    Map<String, Object> headers = new HashMap<>();

    Optional<String> result = tokenExtractor.extractTokenFromHeaders(headers);

    assertTrue(result.isEmpty());
  }

  @Test
  void extractTokenFromHeaders_authorizationWithoutBearer_returnsEmpty() {
    Map<String, Object> headers = new HashMap<>();
    Optional<String> result = tokenExtractor.extractTokenFromHeaders(headers);

    assertFalse(result.isPresent());
  }

  @Test
  void extractTokenFromHeaders_fromAuthAttribute_returnsToken() {
    String expectedToken = "auth-attribute-token";
    Map<String, Object> headers = new HashMap<>();
    headers.put("auth", expectedToken);

    Optional<String> result = tokenExtractor.extractTokenFromHeaders(headers);

    assertTrue(result.isPresent());
    assertEquals(expectedToken, result.get());
  }

  @Test
  void extractTokenFromHeaders_noTokenSources_returnsEmpty() {
    Map<String, Object> headers = new HashMap<>();

    Optional<String> result = tokenExtractor.extractTokenFromHeaders(headers);

    assertFalse(result.isPresent());
  }

  @Test
  void extractTokenFromHeaders_nullNativeHeaders_returnsEmpty() {
    Map<String, Object> headers = new HashMap<>();
    headers.put("nativeHeaders", null);

    Optional<String> result = tokenExtractor.extractTokenFromHeaders(headers);

    assertFalse(result.isPresent());
  }
}

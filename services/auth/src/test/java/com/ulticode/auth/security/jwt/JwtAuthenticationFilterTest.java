package com.ulticode.auth.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.core.context.SecurityContextHolder.clearContext;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, new JwtProperties());
        clearContext();
    }

    @AfterEach
    void tearDown() {
        clearContext();
    }

    @Test
    void cookieTakesPrecedenceAndConsumesVerifiedClaimsOnce() throws Exception {
        Claims claims = claims("user-1", "alice", "ADMIN", null);
        when(jwtTokenProvider.parseToken("cookie-token")).thenReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", "cookie-token"));
        request.addHeader("Authorization", "Bearer header-token");

        doFilter(request);

        assertAuthenticatedAs("user-1", "alice", "ROLE_ADMIN");
        verify(jwtTokenProvider).parseToken("cookie-token");
        verify(jwtTokenProvider, never()).parseToken("header-token");
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(jwtTokenProvider, never()).getUserIdFromToken(anyString());
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString());
        verify(jwtTokenProvider, never()).getRoleFromToken(anyString());
    }

    @Test
    void bearerHeaderIsUsedWhenAccessCookieIsMissing() throws Exception {
        Claims claims = claims("user-2", "bob", "USER", null);
        when(jwtTokenProvider.parseToken("header-token")).thenReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");

        doFilter(request);

        assertAuthenticatedAs("user-2", "bob", "ROLE_USER");
        verify(jwtTokenProvider).parseToken("header-token");
    }

    @Test
    void refreshTokenIsNotAuthenticatedEvenWhenItContainsIdentityClaims() throws Exception {
        Claims claims = claims("user-3", "carol", "USER", "refresh");
        when(jwtTokenProvider.parseToken("refresh-token")).thenReturn(claims);

        MockHttpServletRequest request = requestWithCookie("refresh-token");

        doFilter(request);

        assertThat(getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider).parseToken("refresh-token");
    }

    @Test
    void missingIdentityClaimsRemainUnauthenticated() throws Exception {
        Claims missingUserId = claims(null, "dave", "USER", null);
        Claims missingUsername = claims("user-4", null, "USER", null);
        when(jwtTokenProvider.parseToken("missing-user-id")).thenReturn(missingUserId);
        when(jwtTokenProvider.parseToken("missing-username")).thenReturn(missingUsername);

        doFilter(requestWithCookie("missing-user-id"));
        assertThat(getContext().getAuthentication()).isNull();

        doFilter(requestWithCookie("missing-username"));
        assertThat(getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider).parseToken("missing-user-id");
        verify(jwtTokenProvider).parseToken("missing-username");
    }

    @Test
    void invalidMalformedAndUnsupportedTokensRemainUnauthenticated() throws Exception {
        when(jwtTokenProvider.parseToken("invalid-token")).thenReturn(null);
        when(jwtTokenProvider.parseToken("malformed-token"))
                .thenThrow(new MalformedJwtException("malformed"));
        when(jwtTokenProvider.parseToken("unsupported-token"))
                .thenThrow(new UnsupportedJwtException("unsupported"));

        doFilter(requestWithCookie("invalid-token"));
        assertThat(getContext().getAuthentication()).isNull();

        doFilter(requestWithCookie("malformed-token"));
        assertThat(getContext().getAuthentication()).isNull();

        doFilter(requestWithCookie("unsupported-token"));
        assertThat(getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredTokensClearAuthenticationAndContinueTheChain() throws Exception {
        when(jwtTokenProvider.parseToken("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));
        MockHttpServletRequest request = requestWithCookie("expired-token");
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider).parseToken("expired-token");
    }

    private MockHttpServletRequest requestWithCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", token));
        return request;
    }

    private void doFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private void assertAuthenticatedAs(String userId, String username, String authority) {
        Authentication authentication = getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UserDetails.class);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        assertThat(userDetails.getUsername()).isEqualTo(userId);
        assertThat(authentication.getDetails()).isEqualTo(username);
        assertThat(userDetails.getAuthorities())
                .extracting(grantedAuthority -> grantedAuthority.getAuthority())
                .containsExactly(authority);
    }

    private Claims claims(String userId, String username, String role, String type) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId);
        when(claims.get("username", String.class)).thenReturn(username);
        when(claims.get("role", String.class)).thenReturn(role);
        when(claims.get("type", String.class)).thenReturn(type);
        return claims;
    }
}

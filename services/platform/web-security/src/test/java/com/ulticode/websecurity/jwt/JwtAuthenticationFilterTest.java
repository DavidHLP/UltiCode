package com.ulticode.websecurity.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private final AccessTokenVerifier verifier = mock(AccessTokenVerifier.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesVerifiedCookieTokenAndMapsRole() throws Exception {
        when(verifier.verify("cookie-token"))
                .thenReturn(new AccessTokenClaims("user-1", "alice", "ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        request.setCookies(new Cookie("access_token", "cookie-token"));
        AtomicBoolean reached = apply(request);

        assertThat(reached).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user-1");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void cookieTokenTakesPrecedenceOverBearerHeader() throws Exception {
        when(verifier.verify("cookie-token"))
                .thenReturn(new AccessTokenClaims("user-1", "alice", "USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.setCookies(new Cookie("access_token", "cookie-token"));
        request.addHeader("Authorization", "Bearer bearer-token");

        apply(request);

        verify(verifier).verify("cookie-token");
    }

    @Test
    void bearerOnlyRequestUsesBearerToken() throws Exception {
        when(verifier.verify("bearer-token"))
                .thenReturn(new AccessTokenClaims("service-1", "service", "USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.addHeader("Authorization", "Bearer bearer-token");

        apply(request);

        verify(verifier).verify("bearer-token");
    }

    @Test
    void invalidTokenClearsAuthenticationAndContinues() throws Exception {
        when(verifier.verify("invalid")).thenThrow(new IllegalArgumentException("invalid"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.setCookies(new Cookie("access_token", "invalid"));

        AtomicBoolean reached = apply(request);

        assertThat(reached).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private AtomicBoolean apply(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> reached.set(true);
        filter.doFilter(request, response, chain);
        return reached;
    }
}

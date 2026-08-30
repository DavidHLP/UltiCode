package com.ulticode.auth.security.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CsrfValidationFilterContractTest {

    private final CsrfService csrfService = mock(CsrfService.class);
    private final CsrfValidationFilter filter = new CsrfValidationFilter(csrfService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshCookieRequiresCsrfEvenWithoutAccessAuthentication() throws Exception {
        MockHttpServletRequest request = request("/auth/refresh");
        request.setCookies(new Cookie("refresh_token", "refresh"), new Cookie("csrf_token", "csrf"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, chain(reached));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(reached).isFalse();
    }

    @Test
    void bearerOnlyMutationDoesNotRequireBrowserCsrf() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-1", null, "ROLE_USER"));
        MockHttpServletRequest request = request("/api/v1/problems");
        request.addHeader("Authorization", "Bearer service-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, chain(reached));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(reached).isTrue();
    }

    @Test
    void cookieMutationRejectsHeaderCookieMismatchBeforeBusinessCode() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-1", null, "ROLE_USER"));
        MockHttpServletRequest request = request("/api/v1/problems");
        request.setCookies(new Cookie("access_token", "access"), new Cookie("csrf_token", "cookie-token"));
        request.addHeader("X-CSRF-Token", "header-token");
        when(csrfService.validateAndRotateToken("user-1", "header-token")).thenReturn("rotated-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, chain(reached));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(reached).isFalse();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static FilterChain chain(AtomicBoolean reached) {
        return (request, response) -> reached.set(true);
    }
}

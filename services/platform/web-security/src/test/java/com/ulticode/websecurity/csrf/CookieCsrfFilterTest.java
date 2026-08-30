package com.ulticode.websecurity.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CookieCsrfFilterTest {

    private final CookieCsrfFilter filter = new CookieCsrfFilter();

    @ParameterizedTest
    @ValueSource(strings = {"auth", "app", "admin", "notification"})
    void matchingDoubleSubmitTokenProtectsEveryHttpOwner(String owner) throws Exception {
        Exchange exchange = exchange("POST", "/" + owner + "/mutation");
        exchange.request().setCookies(
                cookie(CookieCsrfFilter.ACCESS_TOKEN_COOKIE, "access"),
                cookie(CookieCsrfFilter.CSRF_TOKEN_COOKIE, "csrf"));
        exchange.request().addHeader(CookieCsrfFilter.CSRF_HEADER, "csrf");

        apply(exchange);

        assertThat(exchange.response().getStatus()).isEqualTo(200);
        assertThat(exchange.reached()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS", "TRACE"})
    void safeMethodsDoNotRequireCsrf(String method) throws Exception {
        Exchange exchange = exchange(method, "/app/read");
        exchange.request().setCookies(cookie(CookieCsrfFilter.ACCESS_TOKEN_COOKIE, "access"));

        apply(exchange);

        assertThat(exchange.response().getStatus()).isEqualTo(200);
        assertThat(exchange.reached()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/refresh", "/auth/logout"})
    void refreshAndLogoutRequireCsrfWithoutAccessAuthentication(String uri) throws Exception {
        Exchange exchange = exchange("POST", uri);
        exchange.request().setCookies(
                cookie(CookieCsrfFilter.REFRESH_TOKEN_COOKIE, "refresh"),
                cookie(CookieCsrfFilter.CSRF_TOKEN_COOKIE, "csrf"));

        apply(exchange);

        assertThat(exchange.response().getStatus()).isEqualTo(403);
        assertThat(exchange.response().getContentAsString()).contains("CSRF token is required");
        assertThat(exchange.reached()).isFalse();
    }

    @Test
    void invalidDoubleSubmitTokenIsRejected() throws Exception {
        Exchange exchange = exchange("PATCH", "/admin/settings");
        exchange.request().setCookies(
                cookie(CookieCsrfFilter.ACCESS_TOKEN_COOKIE, "access"),
                cookie(CookieCsrfFilter.CSRF_TOKEN_COOKIE, "cookie-token"));
        exchange.request().addHeader(CookieCsrfFilter.CSRF_HEADER, "header-token");

        apply(exchange);

        assertThat(exchange.response().getStatus()).isEqualTo(403);
        assertThat(exchange.response().getContentAsString()).contains("Invalid CSRF token");
        assertThat(exchange.response().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(exchange.reached()).isFalse();
    }

    @Test
    void bearerOnlyMutationDoesNotRequireBrowserCsrf() throws Exception {
        Exchange exchange = exchange("POST", "/app/mutation");
        exchange.request().addHeader("Authorization", "Bearer service-token");

        apply(exchange);

        assertThat(exchange.response().getStatus()).isEqualTo(200);
        assertThat(exchange.reached()).isTrue();
    }

    @Test
    void bearerHeaderCannotBypassCsrfWhenCredentialCookieIsPresent() throws Exception {
        Exchange exchange = exchange("DELETE", "/notification/subscription");
        exchange.request().setCookies(cookie(CookieCsrfFilter.ACCESS_TOKEN_COOKIE, "access"));
        exchange.request().addHeader("Authorization", "Bearer service-token");

        apply(exchange);

        assertThat(exchange.response().getStatus()).isEqualTo(403);
        assertThat(exchange.reached()).isFalse();
    }

    @Test
    void credentiallessMutationRemainsOutsideBrowserSessionCsrf() throws Exception {
        Exchange exchange = exchange("POST", "/auth/login");

        apply(exchange);

        assertThat(exchange.response().getStatus()).isEqualTo(200);
        assertThat(exchange.reached()).isTrue();
    }

    private void apply(Exchange exchange) throws Exception {
        filter.doFilter(exchange.request(), exchange.response(), chain(exchange.reachedFlag()));
    }

    private static Exchange exchange(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return new Exchange(request, new MockHttpServletResponse(), new AtomicBoolean());
    }

    private static Cookie cookie(String name, String value) {
        return new Cookie(name, value);
    }

    private static FilterChain chain(AtomicBoolean reached) {
        return (request, response) -> reached.set(true);
    }

    private record Exchange(
            MockHttpServletRequest request,
            MockHttpServletResponse response,
            AtomicBoolean reachedFlag) {
        boolean reached() {
            return reachedFlag.get();
        }
    }
}

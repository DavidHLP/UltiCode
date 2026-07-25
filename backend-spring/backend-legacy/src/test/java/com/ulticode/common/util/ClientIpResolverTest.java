package com.ulticode.common.util;

import org.mockito.Mock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * ClientIpResolver unit tests.
 *
 * <p>Pins the unified header-precedence rule that previously lived in three
 * divergent copies (AuditAspect, RateLimitAspect, AuditHelper). The rule:
 * X-Forwarded-For (leftmost) &rarr; X-Real-IP &rarr; remoteAddr &rarr; "unknown".
 */
@DisplayName("ClientIpResolver")
class ClientIpResolverTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("resolve(HttpServletRequest)")
    class ResolveFromRequest {

        @Test
        @DisplayName("returns leftmost X-Forwarded-For when present")
        void xForwardedForLeftmost() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 10.0.0.2");

            assertEquals("203.0.113.5", resolver.resolve(request));
        }

        @Test
        @DisplayName("trims whitespace around X-Forwarded-For value")
        void xForwardedForTrimmed() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "  203.0.113.5  , 10.0.0.1");

            assertEquals("203.0.113.5", resolver.resolve(request));
        }

        @Test
        @DisplayName("falls back to X-Real-IP when X-Forwarded-For absent")
        void xRealIpFallback() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Real-IP", "198.51.100.7");

            assertEquals("198.51.100.7", resolver.resolve(request));
        }

        @Test
        @DisplayName("prefers X-Forwarded-For over X-Real-IP")
        void forwardedBeatsRealIp() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.5");
            request.addHeader("X-Real-IP", "198.51.100.7");

            assertEquals("203.0.113.5", resolver.resolve(request));
        }

        @Test
        @DisplayName("falls back to remoteAddr when no proxy headers")
        void remoteAddrFallback() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.0.2.99");

            assertEquals("192.0.2.99", resolver.resolve(request));
        }

        @Test
        @DisplayName("skips 'unknown' sentinel in X-Forwarded-For, tries X-Real-IP")
        void skipsUnknownSentinel() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "unknown");
            request.addHeader("X-Real-IP", "198.51.100.7");

            assertEquals("198.51.100.7", resolver.resolve(request));
        }

        @Test
        @DisplayName("skips blank X-Forwarded-For, tries X-Real-IP")
        void skipsBlankHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "   ");
            request.addHeader("X-Real-IP", "198.51.100.7");

            assertEquals("198.51.100.7", resolver.resolve(request));
        }

        @Test
        @DisplayName("returns 'unknown' when request is null")
        void nullRequest() {
            assertEquals("unknown", resolver.resolve(null));
        }

        @Test
        @DisplayName("returns 'unknown' when all sources absent and remoteAddr null")
        void allAbsent() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn(null);

            assertEquals("unknown", resolver.resolve(request));
        }
    }

    @Nested
    @DisplayName("resolveCurrent()")
    class ResolveFromContext {

        @Test
        @DisplayName("resolves from RequestContextHolder")
        void fromContext() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.5");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            assertEquals("203.0.113.5", resolver.resolveCurrent());
        }

        @Test
        @DisplayName("returns 'unknown' when no request bound to thread")
        void noRequestBound() {
            assertEquals("unknown", resolver.resolveCurrent());
        }
    }

    @Test
    @DisplayName("is a Spring component (non-null after construction)")
    void isConstructable() {
        assertNotNull(new ClientIpResolver());
    }
}

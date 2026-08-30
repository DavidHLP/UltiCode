package com.ulticode.auth.session;

import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.security.csrf.CsrfService;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthSessionAdapterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CsrfService csrfService;

    private JwtProperties jwtProperties;
    private DefaultAuthSessionAdapter adapter;
    private SessionCookieAdapter cookieAdapter;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("01234567890123456789012345678901");
        jwtProperties.getCookie().getAccessToken().setSecure(true);
        jwtProperties.getCookie().getAccessToken().setSameSite("Lax");
        jwtProperties.getCookie().getAccessToken().setDomain("example.test");
        jwtProperties.getCookie().getRefreshToken().setSecure(true);
        jwtProperties.getCookie().getRefreshToken().setSameSite("Lax");
        jwtProperties.getCookie().getRefreshToken().setDomain("example.test");
        adapter = new DefaultAuthSessionAdapter(
                jwtTokenProvider, jwtProperties, refreshTokenService, csrfService);
        cookieAdapter = new SessionCookieAdapter();
    }

    @Test
    void loginWritesSecureSameSiteCookieHeaders() {
        AuthSession session = completeLogin();
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieAdapter.apply(session, response);

        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(headers).hasSize(3);
        assertThat(cookie(headers, "access_token"))
                .contains("access_token=access", "Path=/", "Domain=example.test", "Max-Age=900",
                        "Secure", "HttpOnly", "SameSite=Lax");
        assertThat(cookie(headers, "refresh_token"))
                .contains("refresh_token=refresh", "Path=/", "Domain=example.test", "Max-Age=604800",
                        "Secure", "HttpOnly", "SameSite=Lax");
        assertThat(cookie(headers, "csrf_token"))
                .contains("csrf_token=csrf", "Path=/", "Domain=example.test", "Max-Age=900",
                        "Secure", "SameSite=Lax")
                .doesNotContain("HttpOnly");
    }

    @Test
    void refreshWritesTheSameHardenedCookiePolicy() {
        AuthAccountRecord account = account();
        when(jwtTokenProvider.generateAccessToken("user-1", "alice", "USER"))
                .thenReturn("new-access");
        when(csrfService.generateToken("user-1")).thenReturn("new-csrf");
        AuthSession session = adapter.completeRefresh(account, "new-refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieAdapter.apply(session, response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .allSatisfy(header -> assertThat(header).contains("Secure", "SameSite=Lax", "Path=/"));
    }

    @Test
    void logoutClearsCookiesWithTheSameSecurityAttributes() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieAdapter.apply(adapter.clearSession(), response);

        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(headers).hasSize(3).allSatisfy(header -> assertThat(header)
                .contains("Max-Age=0", "Path=/", "Domain=example.test", "Secure", "SameSite=Lax"));
        assertThat(cookie(headers, "access_token")).contains("HttpOnly");
        assertThat(cookie(headers, "refresh_token")).contains("HttpOnly");
        assertThat(cookie(headers, "csrf_token")).doesNotContain("HttpOnly");
    }

    @Test
    void loginReturnsUserAndCookiePolicyAsData() {
        AuthSession session = completeLogin();

        assertThat(session.response().getCsrfToken()).isEqualTo("csrf");
        assertThat(session.response().getUser().id()).isEqualTo("user-1");
        assertThat(session.cookies()).extracting(CookieMutation::name)
                .containsExactly("access_token", "refresh_token", "csrf_token");
        assertThat(session.cookies().get(0))
                .satisfies(cookie -> {
                    assertThat(cookie.value()).isEqualTo("access");
                    assertThat(cookie.maxAgeSeconds()).isEqualTo(900);
                    assertThat(cookie.httpOnly()).isTrue();
                    assertThat(cookie.secure()).isTrue();
                    assertThat(cookie.sameSite()).isEqualTo("Lax");
                    assertThat(cookie.path()).isEqualTo("/");
                    assertThat(cookie.domain()).isEqualTo("example.test");
                });
        assertThat(session.cookies().get(2).httpOnly()).isFalse();
    }

    private AuthSession completeLogin() {
        AuthAccountRecord account = account();
        when(jwtTokenProvider.generateAccessToken("user-1", "alice", "USER"))
                .thenReturn("access");
        when(refreshTokenService.createToken("user-1")).thenReturn("refresh");
        when(csrfService.generateToken("user-1")).thenReturn("csrf");
        return adapter.completeLogin(account);
    }

    private static String cookie(List<String> headers, String name) {
        return headers.stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow();
    }

    private static AuthAccountRecord account() {
        return new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "hash", "USER",
                true, false, null, LocalDateTime.parse("2026-08-06T00:00:00"));
    }
}

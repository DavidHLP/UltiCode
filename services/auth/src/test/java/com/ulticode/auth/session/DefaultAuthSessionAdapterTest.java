package com.ulticode.auth.session;

import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.security.csrf.CsrfService;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthSessionAdapterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CsrfService csrfService;

    private DefaultAuthSessionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultAuthSessionAdapter(
                jwtTokenProvider, jwtProperties, refreshTokenService, csrfService);
    }

    @Test
    void loginReturnsLegacyResponseAndCookiePolicyAsData() {
        AuthAccountRecord account = account();
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(900000L);
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(jwtTokenProvider.generateAccessToken("user-1", "alice", "USER"))
                .thenReturn("access");
        when(refreshTokenService.createToken("user-1")).thenReturn("refresh");
        when(csrfService.generateToken("user-1")).thenReturn("csrf");

        AuthSession session = adapter.completeLogin(account);

        assertThat(session.response().getCsrfToken()).isEqualTo("csrf");
        assertThat(session.response().getUser().id()).isEqualTo("user-1");
        assertThat(session.cookies()).extracting(CookieMutation::name)
                .containsExactly("access_token", "refresh_token", "csrf_token");
        assertThat(session.cookies().get(0))
                .satisfies(cookie -> {
                    assertThat(cookie.value()).isEqualTo("access");
                    assertThat(cookie.maxAgeSeconds()).isEqualTo(900);
                    assertThat(cookie.httpOnly()).isTrue();
                    assertThat(cookie.secure()).isFalse();
                    assertThat(cookie.path()).isEqualTo("/");
                });
        assertThat(session.cookies().get(2).httpOnly()).isFalse();
    }

    @Test
    void clearSessionReturnsThreeDeletionMutations() {
        AuthSession session = adapter.clearSession();

        assertThat(session.response()).isNull();
        assertThat(session.cookies()).extracting(CookieMutation::name)
                .containsExactly("access_token", "refresh_token", "csrf_token");
        assertThat(session.cookies()).allSatisfy(cookie -> assertThat(cookie.maxAgeSeconds()).isZero());
    }

    private static AuthAccountRecord account() {
        return new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "hash", "USER",
                true, false, null, LocalDateTime.parse("2026-08-06T00:00:00"));
    }
}

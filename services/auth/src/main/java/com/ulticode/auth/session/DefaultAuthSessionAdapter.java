package com.ulticode.auth.session;

import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.dto.AuthUserVO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.security.csrf.CsrfService;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default session issuer for JWT, CSRF, and refresh-session state.
 *
 * <p>This class preserves the existing cookie policy as data. The actual
 * Servlet mutation is performed by {@link SessionCookieAdapter}.</p>
 */
@Component
@RequiredArgsConstructor
public class DefaultAuthSessionAdapter implements AuthSessionPort {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    /**
     * Non-HttpOnly CSRF sentinel cookie. The frontend reads this from
     * {@code document.cookie} to detect whether a session exists after a hard
     * page refresh because the access token cookie is HttpOnly. Without this
     * sentinel the auth-core session store skips the {@code /auth/me} bootstrap.
     */
    private static final String CSRF_TOKEN_COOKIE = "csrf_token";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final CsrfService csrfService;

    @Override
    public AuthSession completeLogin(AuthAccountRecord account) {
        String accessToken = jwtTokenProvider.generateAccessToken(account.id(), account.username(), account.role());
        String refreshToken = refreshTokenService.createToken(account.id());
        String csrfToken = csrfService.generateToken(account.id());

        return new AuthSession(
                loginResponse(account, csrfToken),
                List.of(
                        CookieMutation.set(ACCESS_TOKEN_COOKIE, accessToken,
                                (int) (jwtProperties.getAccessTokenExpiration() / 1000), true),
                        CookieMutation.set(REFRESH_TOKEN_COOKIE, refreshToken,
                                (int) (jwtProperties.getRefreshTokenExpiration() / 1000), true),
                        CookieMutation.set(CSRF_TOKEN_COOKIE, csrfToken,
                                (int) (jwtProperties.getAccessTokenExpiration() / 1000), false)
                )
        );
    }

    @Override
    public AuthSession completeRefresh(AuthAccountRecord account, String rotatedRefreshToken) {
        String accessToken = jwtTokenProvider.generateAccessToken(account.id(), account.username(), account.role());
        String csrfToken = csrfService.generateToken(account.id());

        return new AuthSession(
                loginResponse(account, csrfToken),
                List.of(
                        CookieMutation.set(ACCESS_TOKEN_COOKIE, accessToken,
                                (int) (jwtProperties.getAccessTokenExpiration() / 1000), true),
                        CookieMutation.set(REFRESH_TOKEN_COOKIE, rotatedRefreshToken,
                                (int) (jwtProperties.getRefreshTokenExpiration() / 1000), true),
                        CookieMutation.set(CSRF_TOKEN_COOKIE, csrfToken,
                                (int) (jwtProperties.getAccessTokenExpiration() / 1000), false)
                )
        );
    }

    @Override
    public AuthSession clearSession() {
        return new AuthSession(
                null,
                List.of(
                        CookieMutation.clear(ACCESS_TOKEN_COOKIE, true),
                        CookieMutation.clear(REFRESH_TOKEN_COOKIE, true),
                        CookieMutation.clear(CSRF_TOKEN_COOKIE, false)
                )
        );
    }

    private LoginResponse loginResponse(AuthAccountRecord account, String csrfToken) {
        return LoginResponse.builder()
                .csrfToken(csrfToken)
                .user(toUserVO(account))
                .build();
    }

    private AuthUserVO toUserVO(AuthAccountRecord account) {
        return new AuthUserVO(
                account.id(),
                account.username(),
                account.username(),
                account.email() != null ? account.email() : "",
                account.role(),
                Boolean.TRUE.equals(account.isActive()),
                Boolean.TRUE.equals(account.isBanned()),
                account.joinedAt() != null ? account.joinedAt().toString() : ""
        );
    }
}

package com.ulticode.auth.session;

import static com.ulticode.websecurity.csrf.CookieCsrfFilter.CSRF_TOKEN_COOKIE;

import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.dto.AuthUserVO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Default session issuer for JWT, CSRF, and refresh-session state.
 *
 * <p>This class preserves the configured cookie policy as data. The actual
 * Servlet mutation is performed by {@link SessionCookieAdapter}.</p>
 */
@Component
@RequiredArgsConstructor
public class DefaultAuthSessionAdapter implements AuthSessionPort {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    @Override
    public AuthSession completeLogin(AuthAccountRecord account) {
        String accessToken = jwtTokenProvider.generateAccessToken(account.id(), account.username(), account.role());
        String refreshToken = refreshTokenService.createToken(account.id());
        String csrfToken = newCsrfToken();

        return new AuthSession(
                loginResponse(account, csrfToken),
                List.of(
                        accessCookie(accessToken, accessConfig().getMaxAge()),
                        refreshCookie(refreshToken, refreshConfig().getMaxAge()),
                        csrfCookie(csrfToken, refreshConfig().getMaxAge())
                )
        );
    }

    @Override
    public AuthSession completeRefresh(AuthAccountRecord account, String rotatedRefreshToken) {
        String accessToken = jwtTokenProvider.generateAccessToken(account.id(), account.username(), account.role());
        String csrfToken = newCsrfToken();

        return new AuthSession(
                loginResponse(account, csrfToken),
                List.of(
                        accessCookie(accessToken, accessConfig().getMaxAge()),
                        refreshCookie(rotatedRefreshToken, refreshConfig().getMaxAge()),
                        csrfCookie(csrfToken, refreshConfig().getMaxAge())
                )
        );
    }

    @Override
    public AuthSession clearSession() {
        return new AuthSession(
                null,
                List.of(
                        accessCookie("", 0),
                        refreshCookie("", 0),
                        csrfCookie("", 0)
                )
        );
    }

    private static String newCsrfToken() {
        return UUID.randomUUID().toString();
    }

    private CookieMutation accessCookie(String value, int maxAgeSeconds) {
        JwtProperties.AccessTokenCookie config = accessConfig();
        return new CookieMutation(config.getName(), value, maxAgeSeconds, config.isHttpOnly(),
                config.isSecure(), config.getSameSite(), config.getPath(), config.getDomain());
    }

    private CookieMutation refreshCookie(String value, int maxAgeSeconds) {
        JwtProperties.RefreshTokenCookie config = refreshConfig();
        return new CookieMutation(config.getName(), value, maxAgeSeconds, config.isHttpOnly(),
                config.isSecure(), config.getSameSite(), config.getPath(), config.getDomain());
    }

    /**
     * The CSRF cookie must outlive every credential cookie. {@code CookieCsrfFilter}
     * demands the header whenever an access or refresh cookie is present, so a CSRF
     * cookie tied to the 15-minute access lifetime would vanish while the 7-day
     * refresh cookie remains: login and refresh are themselves POST endpoints under
     * the same filter, and {@code /auth/me} needs a valid access token, so the
     * client could never mint a token again.
     */
    private CookieMutation csrfCookie(String value, int maxAgeSeconds) {
        JwtProperties.AccessTokenCookie config = accessConfig();
        return new CookieMutation(CSRF_TOKEN_COOKIE, value, maxAgeSeconds, false,
                config.isSecure(), config.getSameSite(), config.getPath(), config.getDomain());
    }

    private JwtProperties.AccessTokenCookie accessConfig() {
        return jwtProperties.getCookie().getAccessToken();
    }

    private JwtProperties.RefreshTokenCookie refreshConfig() {
        return jwtProperties.getCookie().getRefreshToken();
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

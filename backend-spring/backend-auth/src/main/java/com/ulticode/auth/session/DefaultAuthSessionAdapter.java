package com.ulticode.auth.session;

import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.security.csrf.CsrfService;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Default session adapter that manages JWT cookies, CSRF tokens, and refresh sessions.
 */
@Component
@RequiredArgsConstructor
public class DefaultAuthSessionAdapter implements AuthSessionPort {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final CsrfService csrfService;

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    @Override
    public LoginResponse completeLogin(AuthAccountRecord account, HttpServletResponse response) {
        String accessToken = jwtTokenProvider.generateAccessToken(account.id(), account.username(), account.role());
        setCookie(response, ACCESS_TOKEN_COOKIE, accessToken, (int) (jwtProperties.getAccessTokenExpiration() / 1000));

        String refreshToken = refreshTokenService.createToken(account.id());
        setCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, (int) (jwtProperties.getRefreshTokenExpiration() / 1000));

        String csrfToken = csrfService.generateToken(account.id());
        UserIdentityDTO identity = toUserIdentity(account);

        return LoginResponse.builder()
                .csrfToken(csrfToken)
                .user(identity)
                .build();
    }

    @Override
    public LoginResponse completeRefresh(AuthAccountRecord account, String rotatedRefreshToken, HttpServletResponse response) {
        String accessToken = jwtTokenProvider.generateAccessToken(account.id(), account.username(), account.role());
        setCookie(response, ACCESS_TOKEN_COOKIE, accessToken, (int) (jwtProperties.getAccessTokenExpiration() / 1000));

        setCookie(response, REFRESH_TOKEN_COOKIE, rotatedRefreshToken, (int) (jwtProperties.getRefreshTokenExpiration() / 1000));

        String csrfToken = csrfService.generateToken(account.id());
        UserIdentityDTO identity = toUserIdentity(account);

        return LoginResponse.builder()
                .csrfToken(csrfToken)
                .user(identity)
                .build();
    }

    @Override
    public void clearSession(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE);
        clearCookie(response, REFRESH_TOKEN_COOKIE);
    }

    private UserIdentityDTO toUserIdentity(AuthAccountRecord account) {
        return new UserIdentityDTO(
                account.id(),
                account.username(),
                account.role(),
                Boolean.TRUE.equals(account.isActive()),
                Boolean.TRUE.equals(account.isBanned())
        );
    }

    private void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, String name) {
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}

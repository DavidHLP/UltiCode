package com.ulticode.modules.auth.session;

import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Deep module that owns the entire post-auth tail.
 *
 * <p>Before this module existed, {@code AuthServiceImpl.login/register/refresh}
 * and {@code OAuthService.createOrUpdateUser} each duplicated the same four
 * steps in slightly different shapes:
 * <ol>
 *   <li>generate access JWT</li>
 *   <li>rotate refresh token</li>
 *   <li>set HttpOnly auth + refresh cookies</li>
 *   <li>generate and set CSRF cookie, build {@link LoginResponse}</li>
 * </ol>
 *
 * <p>Cookie flags, refresh rotation, CSRF coupling, and secret rotation are
 * now changed in one place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionModule implements AuthSessionPort {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final CsrfService csrfService;
    private final UserReadProjection userReadProjection;

    @Override
    public LoginResponse completeLogin(User user, HttpServletResponse response) {
        // 1. Generate access JWT
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        // 2. Rotate refresh token
        String refreshToken = refreshTokenService.createToken(user.getId());

        // 3. Set auth + refresh cookies (HttpOnly, SameSite from config)
        setAccessTokenCookie(response, accessToken);
        setRefreshTokenCookie(response, refreshToken);

        // 4. CSRF token + cookie + LoginResponse
        String csrfToken = csrfService.generateToken(user.getId());
        setCsrfCookie(response, csrfToken);
        UserVO userVO = userReadProjection.toVO(user);

        return LoginResponse.builder()
                .csrfToken(csrfToken)
                .user(userVO)
                .build();
    }

    @Override
    public LoginResponse completeRefresh(User user, String rotatedRefreshToken, HttpServletResponse response) {
        // Access token is fresh on every refresh
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        setAccessTokenCookie(response, accessToken);
        setRefreshTokenCookie(response, rotatedRefreshToken);

        String csrfToken = csrfService.generateToken(user.getId());
        setCsrfCookie(response, csrfToken);
        UserVO userVO = userReadProjection.toVO(user);

        return LoginResponse.builder()
                .csrfToken(csrfToken)
                .user(userVO)
                .build();
    }

    @Override
    public void clearSession(HttpServletResponse response) {
        JwtProperties.CookieConfig cookieConfig = jwtProperties.getCookie();
        String secureAttr = cookieConfig.getAccessToken().isSecure() ? "; Secure" : "";

        // Clear access_token
        response.addHeader("Set-Cookie", String.format("%s=; Path=%s; Max-Age=0; HttpOnly%s; SameSite=%s",
                cookieConfig.getAccessToken().getName(),
                cookieConfig.getAccessToken().getPath(),
                secureAttr,
                cookieConfig.getAccessToken().getSameSite()
        ));

        // Clear refresh_token
        response.addHeader("Set-Cookie", String.format("%s=; Path=%s; Max-Age=0; HttpOnly%s; SameSite=%s",
                cookieConfig.getRefreshToken().getName(),
                cookieConfig.getRefreshToken().getPath(),
                secureAttr,
                cookieConfig.getRefreshToken().getSameSite()
        ));

        // Clear csrf_token
        response.addHeader("Set-Cookie", "csrf_token=; Path=/; Max-Age=0; SameSite=Lax" + secureAttr);
    }

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        JwtProperties.AccessTokenCookie cookieConfig = jwtProperties.getCookie().getAccessToken();
        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
                cookieConfig.getName(),
                token,
                cookieConfig.getPath(),
                cookieConfig.getMaxAge(),
                cookieConfig.isSecure() ? "; Secure" : "",
                cookieConfig.getSameSite()
        );
        response.addHeader("Set-Cookie", headerValue);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        JwtProperties.RefreshTokenCookie cookieConfig = jwtProperties.getCookie().getRefreshToken();
        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
                cookieConfig.getName(),
                token,
                cookieConfig.getPath(),
                cookieConfig.getMaxAge(),
                cookieConfig.isSecure() ? "; Secure" : "",
                cookieConfig.getSameSite()
        );
        response.addHeader("Set-Cookie", headerValue);
    }

    private void setCsrfCookie(HttpServletResponse response, String csrfToken) {
        JwtProperties.AccessTokenCookie accessCookie = jwtProperties.getCookie().getAccessToken();
        String secureAttr = accessCookie.isSecure() ? "; Secure" : "";
        response.addHeader("Set-Cookie",
                "csrf_token=" + csrfToken + "; Path=/; Max-Age=86400; SameSite=Lax" + secureAttr);
    }
}

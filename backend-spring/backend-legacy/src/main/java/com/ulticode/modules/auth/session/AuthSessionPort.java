package com.ulticode.modules.auth.session;

import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.user.entity.User;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Port for completing an authenticated session.
 *
 * <p>Callers (AuthService, OAuthService) only see this port; the deep
 * {@link AuthSessionModule} owns the cookie + CSRF + JWT + refresh wiring.
 * Tests can swap in an in-memory adapter to assert on the {@code UserVO}
 * and {@code LoginResponse} without touching the cookie layer.
 */
public interface AuthSessionPort {

    /**
     * Complete a brand-new login: generate access + refresh tokens, set cookies,
     * generate CSRF, return the LoginResponse.
     */
    LoginResponse completeLogin(User user, HttpServletResponse response);

    /**
     * Complete a token refresh: replace access + refresh cookies, regenerate CSRF.
     * Returns the same LoginResponse shape as {@link #completeLogin} so callers
     * can treat the two paths uniformly.
     */
    LoginResponse completeRefresh(User user, String rotatedRefreshToken, HttpServletResponse response);

    /**
     * Clear all auth-related cookies. Used by logout.
     */
    void clearSession(HttpServletResponse response);
}

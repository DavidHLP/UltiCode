package com.ulticode.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties.
 * Binds to jwt.* properties in application.yml
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Secret key for signing JWT tokens.
     * Must be at least 256 bits (32 characters) for HS256 algorithm.
     */
    private String secret;

    /**
     * Access token configuration
     */
    private AccessTokenConfig accessToken = new AccessTokenConfig();

    /**
     * Refresh token configuration
     */
    private RefreshTokenConfig refreshToken = new RefreshTokenConfig();

    /**
     * Cookie configuration for JWT token storage
     */
    private CookieConfig cookie = new CookieConfig();

    @Data
    public static class AccessTokenConfig {
        /**
         * Access token expiration time in milliseconds.
         * Default: 15 minutes (900000 ms)
         */
        private Long expiration = 900000L;
    }

    @Data
    public static class RefreshTokenConfig {
        /**
         * Refresh token expiration time in milliseconds.
         * Default: 7 days (604800000 ms)
         */
        private Long expiration = 604800000L;
    }

    @Data
    public static class CookieConfig {
        /**
         * Access token cookie configuration
         */
        private AccessTokenCookie accessToken = new AccessTokenCookie();

        /**
         * Refresh token cookie configuration
         */
        private RefreshTokenCookie refreshToken = new RefreshTokenCookie();
    }

    @Data
    public static class AccessTokenCookie {
        /**
         * Cookie name (must match NestJS: access_token)
         */
        private String name = "access_token";

        /**
         * HTTP-only flag (prevents JavaScript access)
         */
        private boolean httpOnly = true;

        /**
         * Secure flag (HTTPS only)
         */
        private boolean secure = true;

        /**
         * SameSite attribute (strict, lax, none)
         */
        private String sameSite = "strict";

        /**
         * Cookie path
         */
        private String path = "/";

        /**
         * Cookie max age in seconds.
         * Default: 15 minutes (900 seconds)
         */
        private int maxAge = 900;
    }

    @Data
    public static class RefreshTokenCookie {
        /**
         * Cookie name (must match NestJS: refresh_token)
         */
        private String name = "refresh_token";

        /**
         * HTTP-only flag (prevents JavaScript access)
         */
        private boolean httpOnly = true;

        /**
         * Secure flag (HTTPS only)
         */
        private boolean secure = true;

        /**
         * SameSite attribute (strict, lax, none)
         */
        private String sameSite = "strict";

        /**
         * Cookie path
         */
        private String path = "/";

        /**
         * Cookie max age in seconds.
         * Default: 7 days (604800 seconds)
         */
        private int maxAge = 604800;
    }

    // Convenience methods for backward compatibility
    public Long getAccessTokenExpiration() {
        return accessToken.getExpiration();
    }

    public Long getRefreshTokenExpiration() {
        return refreshToken.getExpiration();
    }
}

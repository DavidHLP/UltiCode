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
     * Access token expiration time in milliseconds.
     * Default: 15 minutes (900000 ms)
     */
    private long accessTokenExpiration = 900000L;

    /**
     * Refresh token expiration time in milliseconds.
     * Default: 7 days (604800000 ms)
     */
    private long refreshTokenExpiration = 604800000L;

    /**
     * Cookie configuration for JWT token storage
     */
    private CookieConfig cookie = new CookieConfig();

    @Data
    public static class CookieConfig {
        /**
         * Cookie name
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
         * Default: 7 days (604800 seconds)
         */
        private int maxAge = 604800;
    }
}

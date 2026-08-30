package com.ulticode.auth.security.jwt;

import jakarta.annotation.PostConstruct;
import java.util.Objects;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties.
 * Binds to jwt.* properties in application.yml
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties implements EnvironmentAware {

    /**
     * Secret key for signing JWT tokens.
     * Must be at least 256 bits (32 characters) for HS256 algorithm.
     */
    private String secret;

    /**
     * Issuer claim (iss) written into every access and refresh token minted by
     * backend-auth. Resource servers in backend-app and backend-admin require
     * this value via {@code jwt.expected-issuer} and reject any token whose
     * issuer is missing or different. Changing this value is a coordinated
     * contract change with both resource verifiers.
     */
    private String issuer = "ulticode-auth";

    /**
     * Platform audience claim (aud) written into every access token minted by
     * backend-auth. Both resource servers (backend-app, backend-admin) require
     * this value via {@code jwt.expected-audience} so a token minted for the
     * UltiCode platform cannot be replayed against a different audience.
     * Refresh tokens do not carry this claim.
     */
    private String audience = "ulticode-api";

    private transient Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * Validate JWT and cookie policy at application startup.
     * - Refuses to start if secret is null, empty, blank, or shorter than 32 characters
     * - Refuses insecure authentication cookies outside exclusive dev, test, or ci profiles
     */
    @PostConstruct
    public void validateSecret() {
        Objects.requireNonNull(secret, "JWT secret must not be null. Set the 'jwt.secret' property or JWT_SECRET environment variable.");
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT secret must not be blank. Set the 'jwt.secret' property or JWT_SECRET environment variable.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters for HS256");
        }
        validateCookiePolicy();
        log.info("JWT secret and cookie policy validated successfully");
    }

    private void validateCookiePolicy() {
        CookieConfig cookieConfig = Objects.requireNonNull(cookie, "JWT cookie configuration must not be null");
        AccessTokenCookie access = Objects.requireNonNull(
                cookieConfig.getAccessToken(), "Access-token cookie configuration must not be null");
        RefreshTokenCookie refresh = Objects.requireNonNull(
                cookieConfig.getRefreshToken(), "Refresh-token cookie configuration must not be null");

        validateCookie("access-token", access.getName(), access.isHttpOnly(), access.isSecure(),
                access.getSameSite(), access.getPath(), access.getMaxAge());
        validateCookie("refresh-token", refresh.getName(), refresh.isHttpOnly(), refresh.isSecure(),
                refresh.getSameSite(), refresh.getPath(), refresh.getMaxAge());

        if ((!access.isSecure() || !refresh.isSecure()) && !insecureCookiesAllowed()) {
            throw new IllegalStateException(
                    "JWT cookies must use Secure outside the dev, test, or ci profiles");
        }
    }

    private boolean insecureCookiesAllowed() {
        if (environment == null) {
            return false;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (!"dev".equals(profile) && !"test".equals(profile) && !"ci".equals(profile)) {
                return false;
            }
        }
        return true;
    }

    private static void validateCookie(String label, String name, boolean httpOnly, boolean secure,
            String sameSite, String path, int maxAge) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(label + " cookie name must not be blank");
        }
        if (!httpOnly) {
            throw new IllegalStateException(label + " cookie must be HttpOnly");
        }
        if (path == null || !path.startsWith("/")) {
            throw new IllegalStateException(label + " cookie path must start with '/'");
        }
        if (maxAge <= 0) {
            throw new IllegalStateException(label + " cookie max-age must be positive");
        }
        boolean sameSiteKnown = "Strict".equalsIgnoreCase(sameSite)
                || "Lax".equalsIgnoreCase(sameSite)
                || "None".equalsIgnoreCase(sameSite);
        if (!sameSiteKnown) {
            throw new IllegalStateException(label + " cookie SameSite must be Strict, Lax, or None");
        }
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException(label + " cookie SameSite=None requires Secure");
        }
    }

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
         * Optional shared cookie domain. Empty keeps host-only cookies.
         */
        private String domain;

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
         * Optional shared cookie domain. Empty keeps host-only cookies.
         */
        private String domain;

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

package com.ulticode.auth.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token provider for generating and validating JWT tokens.
 * Uses jjwt 0.12.5 API with HS256 algorithm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final RsaKeyManager rsaKeyManager;

    /**
     * Generate the secret key from the configured secret string.
     * The secret must be at least 256 bits (32 characters) for HS256.
     *
     * @return the secret key
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate an access token for a user.
     * Access tokens are short-lived (15 minutes by default).
     *
     * @param userId   the user ID
     * @param username the username
     * @param role     the user role
     * @return the generated JWT access token
     */
    public String generateAccessToken(String userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        var builder = Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .issuedAt(now)
                .expiration(expiryDate);

        // AUTH-COMP-006: use RS256 when enabled, otherwise HS256 (overlap period).
        if (rsaKeyManager.isRsaEnabled() && rsaKeyManager.getPrivateKey() != null) {
            builder.header().keyId(rsaKeyManager.getKeyId()).and()
                    .signWith(rsaKeyManager.getPrivateKey(),
                            Jwts.SIG.RS256);
        } else {
            builder.signWith(getSigningKey());
        }
        return builder.compact();
    }

    /**
     * Generate a refresh token for a user.
     * Refresh tokens are long-lived (7 days by default).
     *
     * @param userId the user ID
     * @return the generated JWT refresh token
     */
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString()) // jti: prevents deterministic hash collision on same-millisecond generation
                .subject(userId)
                .issuer(jwtProperties.getIssuer())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Parse a JWT token and return its claims.
     *
     * @param token the JWT token to parse
     * @return the claims from the token, or null if invalid
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.debug("JWT token is malformed: {}", e.getMessage());
        } catch (SignatureException e) {
            log.debug("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT token is empty or null: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get the user ID from a JWT token.
     *
     * @param token the JWT token
     * @return the user ID, or null if invalid
     */
    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * Get the user ID from a refresh token, rejecting access tokens and other JWT types.
     *
     * @param token the refresh token
     * @return the user ID, or null if the token is invalid or is not a refresh token
     */
    public String getUserIdFromRefreshToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null || !"refresh".equals(claims.get("type", String.class))) {
            return null;
        }
        return claims.getSubject();
    }

    /**
     * Get the username from a JWT token.
     *
     * @param token the JWT token
     * @return the username, or null if invalid
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    /**
     * Get the role from a JWT token.
     *
     * @param token the JWT token
     * @return the role, or null if invalid
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /**
     * Validate a JWT token.
     *
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.debug("JWT token is malformed: {}", e.getMessage());
        } catch (SignatureException e) {
            log.debug("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT token is empty or null: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Check if a JWT token is expired.
     *
     * @param token the JWT token to check
     * @return true if the token is expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (MalformedJwtException | SignatureException | UnsupportedJwtException | IllegalArgumentException e) {
            return true;
        }
    }
}

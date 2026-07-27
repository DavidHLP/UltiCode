package com.ulticode.app.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

/**
 * Resource server offline JWT verifier for backend-app (P2-AUTH-002).
 * Validates Auth-issued tokens locally without making synchronous RPC calls.
 */
@Component
public class ResourceServerJwtVerifier {

    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("HS256", "RS256");

    @Value("${jwt.secret:test-secret-key-must-be-at-least-256-bits-long-for-testing}")
    private String jwtSecret;

    @Value("${jwt.expected-issuer:ulticode-auth}")
    private String expectedIssuer;

    public Claims verifyAndParse(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token must not be null or blank");
        }

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Date now = new Date();
        if (claims.getExpiration() != null && claims.getExpiration().before(now)) {
            throw new IllegalArgumentException("Token has expired");
        }

        if (claims.getNotBefore() != null && claims.getNotBefore().after(now)) {
            throw new IllegalArgumentException("Token is not active yet");
        }

        String type = claims.get("type", String.class);
        if ("refresh".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Refresh tokens cannot be used as access tokens");
        }

        return claims;
    }

    public String getUserId(Claims claims) {
        return claims.getSubject();
    }

    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public Set<String> getAllowedAlgorithms() {
        return ALLOWED_ALGORITHMS;
    }
}

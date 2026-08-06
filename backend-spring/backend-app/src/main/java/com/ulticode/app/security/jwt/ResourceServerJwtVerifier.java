package com.ulticode.app.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

/**
 * Resource server offline JWT verifier for backend-app (P2-AUTH-002).
 * Validates Auth-issued tokens locally without making synchronous RPC calls.
 *
 * <p>AUTH-COMP-007: supports both HS256 (overlap fallback) and RS256.
 * RS256 public keys are fetched from the Auth service's JWKS endpoint via
 * {@link JwksPublicKeyProvider} (HTTP fetch + TTL cache + kid lookup).
 * HS256 remains as a fallback for tokens issued before the RS256 cutover
 * or when the JWKS endpoint is unreachable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceServerJwtVerifier {

    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("HS256", "RS256");

    private final JwksPublicKeyProvider jwksProvider;

    @Value("${jwt.secret:test-secret-key-must-be-at-least-256-bits-long-for-testing}")
    private String jwtSecret;

    @Value("${jwt.expected-issuer:ulticode-auth}")
    private String expectedIssuer;

    @Value("${jwt.expected-audience:ulticode-api}")
    private String expectedAudience;

    public Claims verifyAndParse(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token must not be null or blank");
        }

        String alg = extractAlgorithm(token);
        Claims claims;

        if ("RS256".equals(alg)) {
            String kid = extractKid(token);
            RSAPublicKey rsaKey = jwksProvider.getKey(kid);
            if (rsaKey == null) {
                throw new IllegalArgumentException(
                        "RS256 token rejected: no JWKS public key for kid=" + kid);
            }
            claims = Jwts.parser()
                    .verifyWith(rsaKey)
                    .requireIssuer(expectedIssuer)
                    .requireAudience(expectedAudience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } else if ("HS256".equals(alg)) {
            claims = verifyWithHmac(token);
        } else {
            throw new IllegalArgumentException("Unsupported JWT algorithm: " + alg);
        }

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

    private Claims verifyWithHmac(String token) {
        SecretKey hmacKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(hmacKey)
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String extractAlgorithm(String token) {
        return extractHeaderField(token, "alg");
    }

    private String extractKid(String token) {
        return extractHeaderField(token, "kid");
    }

    private String extractHeaderField(String token, String field) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            int fieldIdx = headerJson.indexOf("\"" + field + "\"");
            if (fieldIdx < 0) return null;
            int colonIdx = headerJson.indexOf(':', fieldIdx);
            int quoteStart = headerJson.indexOf('"', colonIdx + 1);
            int quoteEnd = headerJson.indexOf('"', quoteStart + 1);
            if (quoteStart < 0 || quoteEnd < 0) return null;
            return headerJson.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return null;
        }
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

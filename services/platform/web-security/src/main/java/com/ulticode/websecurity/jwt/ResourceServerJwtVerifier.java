package com.ulticode.websecurity.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;

/** Shared offline verifier for Auth-issued resource-server access tokens. */
public final class ResourceServerJwtVerifier implements AccessTokenVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwksPublicKeyProvider jwksProvider;
    private final String jwtSecret;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Set<String> allowedAlgorithms;
    private final Clock clock;
    private final long clockSkewSeconds;

    public ResourceServerJwtVerifier(
            JwksPublicKeyProvider jwksProvider,
            String jwtSecret,
            String expectedIssuer,
            String expectedAudience,
            Set<String> allowedAlgorithms,
            Clock clock,
            long clockSkewSeconds) {
        if (expectedIssuer == null || expectedIssuer.isBlank()
                || expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("Expected JWT issuer and audience are required");
        }
        if (allowedAlgorithms == null || allowedAlgorithms.isEmpty()
                || !Set.of("HS256", "RS256").containsAll(allowedAlgorithms)) {
            throw new IllegalArgumentException("JWT algorithms must be a non-empty subset of HS256 and RS256");
        }
        if (clockSkewSeconds < 0 || clockSkewSeconds > 300) {
            throw new IllegalArgumentException("JWT clock skew must be between 0 and 300 seconds");
        }
        this.jwksProvider = jwksProvider;
        this.jwtSecret = jwtSecret;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.allowedAlgorithms = Set.copyOf(allowedAlgorithms);
        this.clock = clock;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    @Override
    public AccessTokenClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT token must not be blank");
        }
        JsonNode header = parseHeader(token);
        String algorithm = header.path("alg").asText("");
        if (!allowedAlgorithms.contains(algorithm)) {
            throw new IllegalArgumentException("JWT algorithm is not allowed: " + algorithm);
        }
        String headerType = header.path("typ").asText("");
        if (!headerType.isBlank() && !"JWT".equalsIgnoreCase(headerType)
                && !"at+jwt".equalsIgnoreCase(headerType)) {
            throw new IllegalArgumentException("JWT header type is not an access token");
        }

        Claims claims = "RS256".equals(algorithm)
                ? verifyRsa(token, header.path("kid").asText(""))
                : verifyHmac(token);
        validateTimeAndType(claims);
        return new AccessTokenClaims(
                claims.getSubject(),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }

    public Set<String> getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    private Claims verifyRsa(String token, String kid) {
        if (kid.isBlank()) {
            throw new IllegalArgumentException("RS256 token must contain kid");
        }
        RSAPublicKey key = jwksProvider.getKey(kid);
        if (key == null) {
            throw new IllegalArgumentException("RS256 token has no trusted key for kid=" + kid);
        }
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Claims verifyHmac(String token) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("HS256 JWT secret must be at least 32 characters");
        }
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void validateTimeAndType(Claims claims) {
        Instant now = clock.instant();
        Date expiration = claims.getExpiration();
        if (expiration == null || expiration.toInstant().isBefore(now.minusSeconds(clockSkewSeconds))) {
            throw new IllegalArgumentException("Access token is expired or has no expiration");
        }
        Date notBefore = claims.getNotBefore();
        if (notBefore != null && notBefore.toInstant().isAfter(now.plusSeconds(clockSkewSeconds))) {
            throw new IllegalArgumentException("Access token is not active yet");
        }
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null || issuedAt.toInstant().isAfter(now.plusSeconds(clockSkewSeconds))) {
            throw new IllegalArgumentException("Access token issued-at time is invalid");
        }
        String type = claims.get("type", String.class);
        if (type != null && !"access".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Token type is not access");
        }
    }

    private static JsonNode parseHeader(String token) {
        try {
            int separator = token.indexOf('.');
            if (separator <= 0) {
                throw new IllegalArgumentException("JWT compact form is invalid");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(token.substring(0, separator));
            return MAPPER.readTree(decoded);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT header is invalid", exception);
        }
    }
}

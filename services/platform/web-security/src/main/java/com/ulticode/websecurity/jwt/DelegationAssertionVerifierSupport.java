package com.ulticode.websecurity.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Common RS256, claim, lifetime, and key-id checks for Dubbo assertions. */
public final class DelegationAssertionVerifierSupport {

    public static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    public static final Duration MAX_ASSERTION_LIFETIME = Duration.ofMinutes(1);

    private DelegationAssertionVerifierSupport() {
    }

    public static Claims verify(
            String assertion,
            PublicKey publicKey,
            String expectedKid,
            String expectedIssuer,
            String expectedAudience,
            Clock clock) {
        if (assertion == null || assertion.isBlank()
                || publicKey == null
                || expectedKid == null || expectedKid.isBlank()
                || expectedIssuer == null || expectedIssuer.isBlank()
                || expectedAudience == null || expectedAudience.isBlank()
                || clock == null) {
            throw new IllegalArgumentException("Delegation assertion verification configuration is incomplete");
        }
        if (!(publicKey instanceof RSAPublicKey rsaKey) || rsaKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("Delegation assertion requires a 2048-bit RSA public key");
        }

        Jws<Claims> signed = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build()
                .parseSignedClaims(assertion);
        JwsHeader header = signed.getHeader();
        if (!"RS256".equals(header.getAlgorithm())) {
            throw new IllegalArgumentException("Delegation assertion algorithm is not RS256");
        }
        if (!expectedKid.equals(header.getKeyId())) {
            throw new IllegalArgumentException("Delegation assertion kid is not trusted");
        }
        String headerType = header.getType();
        if (headerType != null && !headerType.isBlank()
                && !"JWT".equalsIgnoreCase(headerType)
                && !"at+jwt".equalsIgnoreCase(headerType)) {
            throw new IllegalArgumentException("Delegation assertion header type is invalid");
        }

        Claims claims = signed.getPayload();
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        if (claims.getId() == null || claims.getId().isBlank()
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || issuedAt == null || expiration == null) {
            throw new IllegalArgumentException("Delegation assertion requires subject, jti, iat, and exp");
        }

        Instant now = clock.instant();
        Instant issued = issuedAt.toInstant();
        Instant expires = expiration.toInstant();
        if (issued.isAfter(now.plus(CLOCK_SKEW))
                || !expires.isAfter(now)
                || expires.isAfter(issued.plus(MAX_ASSERTION_LIFETIME))) {
            throw new IllegalArgumentException("Delegation assertion lifetime is invalid");
        }
        return claims;
    }
}

package com.ulticode.websecurity.jwt;

import com.ulticode.common.security.DelegationAssertionContract;
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

    /**
     * Applies the shared actor, claim, and one-shot replay policy after the
     * transport-specific owner has read its Rpc attachment.
     */
    public static boolean verifyTrusted(
            String actorType,
            String actorId,
            String delegatorId,
            String assertion,
            PublicKey publicKey,
            String keyId,
            PublicKey bootstrapPublicKey,
            String bootstrapKeyId,
            String expectedIssuer,
            String expectedAudience,
            DelegationAssertionReplayGuard replayGuard,
            Clock clock,
            boolean allowBootstrap,
            boolean requireAdminActor) {
        boolean bootstrap = actorType != null && "BOOTSTRAP".equalsIgnoreCase(actorType);
        if (actorType == null || actorType.isBlank()
                || actorId == null || actorId.isBlank()
                || delegatorId == null || delegatorId.isBlank()
                || !actorId.equals(delegatorId)
                || (bootstrap && (!allowBootstrap || !"bootstrap".equals(actorId)))
                || (!bootstrap && requireAdminActor && !isAdminActor(actorType))) {
            return false;
        }

        PublicKey verificationKey = bootstrap ? bootstrapPublicKey : publicKey;
        String verificationKeyId = bootstrap ? bootstrapKeyId : keyId;
        if (assertion == null || assertion.isBlank()
                || verificationKey == null || verificationKeyId == null
                || verificationKeyId.isBlank() || replayGuard == null) {
            return false;
        }

        try {
            Claims claims = verify(
                    assertion,
                    verificationKey,
                    verificationKeyId,
                    expectedIssuer,
                    expectedAudience,
                    clock);
            String claimedActorService = claims.get(
                    DelegationAssertionContract.ACTOR_SERVICE_CLAIM, String.class);
            String claimedActorType = claims.get(
                    DelegationAssertionContract.ACTOR_TYPE_CLAIM, String.class);
            boolean bootstrapClaim = Boolean.TRUE.equals(claims.get(
                    DelegationAssertionContract.BOOTSTRAP_CLAIM, Boolean.class));
            if (!"backend-admin".equals(claimedActorService)
                    || claimedActorType == null
                    || !claimedActorType.equalsIgnoreCase(actorType)
                    || !actorId.equals(claims.getSubject())
                    || bootstrap != bootstrapClaim) {
                return false;
            }
            return replayGuard.claim(expectedAudience, claims.getId(), MAX_ASSERTION_LIFETIME);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isAdminActor(String actorType) {
        return "ADMIN".equalsIgnoreCase(actorType)
                || "SUPER_ADMIN".equalsIgnoreCase(actorType);
    }
}

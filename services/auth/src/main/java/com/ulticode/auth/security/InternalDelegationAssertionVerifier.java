package com.ulticode.auth.security;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Verifies signed Admin identity assertions on Auth-owned write RPCs. */
@Component
public class InternalDelegationAssertionVerifier {

    private static final String BOOTSTRAP_ACTOR_TYPE = "BOOTSTRAP";
    private static final String BOOTSTRAP_ACTOR_ID = "bootstrap";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    private static final Duration MAX_ASSERTION_LIFETIME = Duration.ofMinutes(1);

    @Value("${security.internal-delegation.secret:${jwt.secret:}}")
    private String secret;

    @Value("${security.internal-delegation.bootstrap-secret:}")
    private String bootstrapSecret;

    @Value("${security.internal-delegation.issuer:" + DelegationAssertionContract.ISSUER + "}")
    private String expectedIssuer;

    @Value("${security.internal-delegation.audience:backend-auth}")
    private String expectedAudience;

    /** Spring constructor. */
    public InternalDelegationAssertionVerifier() {
    }

    /** Focused-test constructor retaining the normal assertion seam. */
    InternalDelegationAssertionVerifier(String secret, String expectedIssuer, String expectedAudience) {
        this(secret, expectedIssuer, expectedAudience, "");
    }

    /** Focused-test constructor including the one-shot bootstrap secret. */
    InternalDelegationAssertionVerifier(
            String secret, String expectedIssuer, String expectedAudience, String bootstrapSecret) {
        this.secret = secret;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.bootstrapSecret = bootstrapSecret;
    }

    /** Verify the current Dubbo caller's assertion for the requested actor. */
    public boolean isTrusted(ActorDelegation actor) {
        boolean bootstrap = actor != null
                && BOOTSTRAP_ACTOR_TYPE.equalsIgnoreCase(actor.actorType());
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())
                || (bootstrap && !BOOTSTRAP_ACTOR_ID.equals(actor.actorId()))
                || (!bootstrap && !isAdminRole(actor.actorType()))) {
            return false;
        }

        String assertion = RpcContext.getServerAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
        String verificationSecret = bootstrap ? bootstrapSecret : secret;
        if (assertion == null || assertion.isBlank()
                || verificationSecret == null || verificationSecret.isBlank()) {
            return false;
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(verificationSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(expectedIssuer)
                    .requireAudience(expectedAudience)
                    .build()
                    .parseSignedClaims(assertion)
                    .getPayload();

            Instant now = Instant.now();
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            String actorService = claims.get(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, String.class);
            String actorType = claims.get(DelegationAssertionContract.ACTOR_TYPE_CLAIM, String.class);
            boolean bootstrapClaim = Boolean.TRUE.equals(
                    claims.get(DelegationAssertionContract.BOOTSTRAP_CLAIM, Boolean.class));

            if (claims.getId() == null || claims.getId().isBlank()
                    || issuedAt == null || expiration == null
                    || !"backend-admin".equals(actorService)
                    || actorType == null || !actorType.equalsIgnoreCase(actor.actorType())
                    || !actor.actorId().equals(claims.getSubject())
                    || bootstrap != bootstrapClaim) {
                return false;
            }

            Instant issued = issuedAt.toInstant();
            Instant expires = expiration.toInstant();
            return !issued.isAfter(now.plus(CLOCK_SKEW))
                    && expires.isAfter(now)
                    && !expires.isAfter(issued.plus(MAX_ASSERTION_LIFETIME));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isAdminRole(String actorType) {
        return "ADMIN".equalsIgnoreCase(actorType)
                || "SUPER_ADMIN".equalsIgnoreCase(actorType);
    }
}

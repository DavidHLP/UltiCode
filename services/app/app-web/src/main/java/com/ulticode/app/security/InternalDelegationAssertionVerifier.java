package com.ulticode.app.security;

import com.ulticode.common.command.ActorDelegation;
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

/**
 * Verifies the signed Admin-to-App delegation assertion carried by Dubbo.
 *
 * <p>Dubbo attachments are attacker-controlled transport input. Only the
 * signature and the bound issuer, audience, deadline, jti, service, actor and
 * self-delegation claims make the assertion authoritative.
 */
@Component
public class InternalDelegationAssertionVerifier {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    private static final Duration MAX_ASSERTION_LIFETIME = Duration.ofMinutes(1);

    @Value("${security.internal-delegation.secret:${jwt.secret:}}")
    private String secret;

    @Value("${security.internal-delegation.issuer:" + DelegationAssertionContract.ISSUER + "}")
    private String expectedIssuer;

    @Value("${security.internal-delegation.audience:" + DelegationAssertionContract.AUDIENCE + "}")
    private String expectedAudience;

    /** Spring constructor. */
    public InternalDelegationAssertionVerifier() {
    }

    /** Focused-test constructor. */
    InternalDelegationAssertionVerifier(String secret, String expectedIssuer, String expectedAudience) {
        this.secret = secret;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
    }

    /**
     * Verify the current Dubbo caller's assertion for the requested actor.
     * Missing or malformed transport identity always fails closed.
     */
    public boolean isTrusted(ActorDelegation actor) {
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())) {
            return false;
        }

        String assertion = RpcContext.getServerAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
        if (assertion == null || assertion.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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

            if (claims.getId() == null || claims.getId().isBlank()
                    || issuedAt == null || expiration == null
                    || actorService == null
                    || !DelegationAssertionContract.ISSUER.equals(actorService)
                    || actorType == null || !actorType.equalsIgnoreCase(actor.actorType())
                    || !actor.actorId().equals(claims.getSubject())) {
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
}

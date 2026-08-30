package com.ulticode.app.security;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import com.ulticode.websecurity.jwt.DelegationAssertionReplayGuard;
import com.ulticode.websecurity.jwt.DelegationAssertionVerifierSupport;
import com.ulticode.websecurity.jwt.RsaKeyMaterial;
import io.jsonwebtoken.Claims;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Verifies RS256 Admin identity assertions on App-owned write RPCs. */
@Component
public class InternalDelegationAssertionVerifier {

    private final RSAPublicKey publicKey;
    private final String keyId;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final DelegationAssertionReplayGuard replayGuard;
    private final Clock clock;

    @Autowired
    public InternalDelegationAssertionVerifier(
            @Value("${security.internal-delegation.public-key:}") String publicKeyBase64,
            @Value("${security.internal-delegation.key-id:}") String keyId,
            @Value("${security.internal-delegation.issuer:" + DelegationAssertionContract.ISSUER + "}") String expectedIssuer,
            @Value("${security.internal-delegation.audience:" + DelegationAssertionContract.AUDIENCE + "}") String expectedAudience,
            DelegationAssertionReplayGuard replayGuard) {
        this(publicKeyBase64, keyId, expectedIssuer, expectedAudience, replayGuard, Clock.systemUTC());
    }

    InternalDelegationAssertionVerifier(
            String publicKeyBase64,
            String keyId,
            String expectedIssuer,
            String expectedAudience,
            DelegationAssertionReplayGuard replayGuard,
            Clock clock) {
        this.publicKey = loadOptionalPublicKey(publicKeyBase64);
        this.keyId = keyId;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.replayGuard = replayGuard;
        this.clock = clock;
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
        if (assertion == null || assertion.isBlank() || publicKey == null) {
            return false;
        }

        try {
            Claims claims = DelegationAssertionVerifierSupport.verify(
                    assertion, publicKey, keyId, expectedIssuer, expectedAudience, clock);
            String actorService = claims.get(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, String.class);
            String actorType = claims.get(DelegationAssertionContract.ACTOR_TYPE_CLAIM, String.class);
            if (!"backend-admin".equals(actorService)
                    || actorType == null || !actorType.equalsIgnoreCase(actor.actorType())
                    || !actor.actorId().equals(claims.getSubject())
                    || Boolean.TRUE.equals(claims.get(DelegationAssertionContract.BOOTSTRAP_CLAIM, Boolean.class))) {
                return false;
            }
            return replayGuard != null
                    && replayGuard.claim(expectedAudience, claims.getId(),
                    DelegationAssertionVerifierSupport.MAX_ASSERTION_LIFETIME);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static RSAPublicKey loadOptionalPublicKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return RsaKeyMaterial.loadPublicKey(encoded);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid delegation public key", exception);
        }
    }
}

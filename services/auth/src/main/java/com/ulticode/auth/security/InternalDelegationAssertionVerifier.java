package com.ulticode.auth.security;

import com.ulticode.auth.api.command.ActorDelegation;
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

/** Verifies RS256 Admin identity assertions on Auth-owned write RPCs. */
@Component
public class InternalDelegationAssertionVerifier {

    private static final String BOOTSTRAP_ACTOR_TYPE = "BOOTSTRAP";
    private static final String BOOTSTRAP_ACTOR_ID = "bootstrap";

    private final RSAPublicKey publicKey;
    private final String keyId;
    private final RSAPublicKey bootstrapPublicKey;
    private final String bootstrapKeyId;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final DelegationAssertionReplayGuard replayGuard;
    private final Clock clock;

    @Autowired
    public InternalDelegationAssertionVerifier(
            @Value("${security.internal-delegation.public-key:}") String publicKeyBase64,
            @Value("${security.internal-delegation.key-id:}") String keyId,
            @Value("${security.internal-delegation.bootstrap-public-key:}") String bootstrapPublicKeyBase64,
            @Value("${security.internal-delegation.bootstrap-key-id:}") String bootstrapKeyId,
            @Value("${security.internal-delegation.issuer:" + DelegationAssertionContract.ISSUER + "}") String expectedIssuer,
            @Value("${security.internal-delegation.audience:backend-auth}") String expectedAudience,
            DelegationAssertionReplayGuard replayGuard) {
        this(publicKeyBase64, keyId, bootstrapPublicKeyBase64, bootstrapKeyId,
                expectedIssuer, expectedAudience, replayGuard, Clock.systemUTC());
    }

    InternalDelegationAssertionVerifier(
            String publicKeyBase64,
            String keyId,
            String bootstrapPublicKeyBase64,
            String bootstrapKeyId,
            String expectedIssuer,
            String expectedAudience,
            DelegationAssertionReplayGuard replayGuard,
            Clock clock) {
        this.publicKey = loadOptionalPublicKey(publicKeyBase64, "delegation");
        this.keyId = keyId;
        this.bootstrapPublicKey = loadOptionalPublicKey(bootstrapPublicKeyBase64, "bootstrap delegation");
        this.bootstrapKeyId = bootstrapKeyId;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.replayGuard = replayGuard;
        this.clock = clock;
    }

    /** Verify the current Dubbo caller's assertion for the requested actor. */
    public boolean isTrusted(ActorDelegation actor) {
        boolean bootstrap = actor != null && BOOTSTRAP_ACTOR_TYPE.equalsIgnoreCase(actor.actorType());
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())
                || (bootstrap && !BOOTSTRAP_ACTOR_ID.equals(actor.actorId()))
                || (!bootstrap && !isAdminRole(actor.actorType()))) {
            return false;
        }

        String assertion = RpcContext.getServerAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
        RSAPublicKey verificationKey = bootstrap ? bootstrapPublicKey : publicKey;
        String verificationKeyId = bootstrap ? bootstrapKeyId : keyId;
        if (assertion == null || assertion.isBlank() || verificationKey == null) {
            return false;
        }

        try {
            Claims claims = DelegationAssertionVerifierSupport.verify(
                    assertion, verificationKey, verificationKeyId, expectedIssuer, expectedAudience, clock);
            String actorService = claims.get(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, String.class);
            String actorType = claims.get(DelegationAssertionContract.ACTOR_TYPE_CLAIM, String.class);
            boolean bootstrapClaim = Boolean.TRUE.equals(
                    claims.get(DelegationAssertionContract.BOOTSTRAP_CLAIM, Boolean.class));
            if (!"backend-admin".equals(actorService)
                    || actorType == null || !actorType.equalsIgnoreCase(actor.actorType())
                    || !actor.actorId().equals(claims.getSubject())
                    || bootstrap != bootstrapClaim) {
                return false;
            }
            return replayGuard != null
                    && replayGuard.claim(expectedAudience, claims.getId(),
                    DelegationAssertionVerifierSupport.MAX_ASSERTION_LIFETIME);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isAdminRole(String actorType) {
        return "ADMIN".equalsIgnoreCase(actorType) || "SUPER_ADMIN".equalsIgnoreCase(actorType);
    }

    private static RSAPublicKey loadOptionalPublicKey(String encoded, String label) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return RsaKeyMaterial.loadPublicKey(encoded);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid " + label + " public key", exception);
        }
    }
}

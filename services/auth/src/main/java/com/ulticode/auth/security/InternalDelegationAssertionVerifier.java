package com.ulticode.auth.security;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import com.ulticode.common.security.LocalDelegationAssertionContext;
import com.ulticode.websecurity.jwt.DelegationAssertionReplayGuard;
import com.ulticode.websecurity.jwt.DelegationAssertionVerifierSupport;
import com.ulticode.websecurity.jwt.RsaKeyMaterial;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Verifies RS256 Admin identity assertions on Auth-owned write RPCs. */
@Component
public class InternalDelegationAssertionVerifier {

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
        this.publicKey = RsaKeyMaterial.loadOptionalPublicKey(publicKeyBase64, "delegation");
        this.keyId = keyId;
        this.bootstrapPublicKey = RsaKeyMaterial.loadOptionalPublicKey(
                bootstrapPublicKeyBase64, "bootstrap delegation");
        this.bootstrapKeyId = bootstrapKeyId;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.replayGuard = replayGuard;
        this.clock = clock;
    }

    /** Verify a Dubbo attachment or a scoped same-process assertion. */
    public boolean isTrusted(ActorDelegation actor) {
        String assertion = LocalDelegationAssertionContext.current();
        if (assertion == null) {
            assertion = RpcContext.getServerAttachment().getAttachment(
                    DelegationAssertionContract.ATTACHMENT_KEY);
        }
        return DelegationAssertionVerifierSupport.verifyTrusted(
                actor == null ? null : actor.actorType(),
                actor == null ? null : actor.actorId(),
                actor == null ? null : actor.delegatorId(),
                assertion,
                publicKey,
                keyId,
                bootstrapPublicKey,
                bootstrapKeyId,
                expectedIssuer,
                expectedAudience,
                replayGuard,
                clock,
                true,
                true);
    }
}

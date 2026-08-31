package com.ulticode.submission.security;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import com.ulticode.websecurity.jwt.DelegationAssertionReplayGuard;
import com.ulticode.websecurity.jwt.DelegationAssertionVerifierSupport;
import com.ulticode.websecurity.jwt.RsaKeyMaterial;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

/** Verifies Admin's short-lived RS256 assertion before owner mutations. */
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
            @Value("${security.internal-delegation.audience:backend-submission}") String expectedAudience,
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
        this.publicKey = RsaKeyMaterial.loadOptionalPublicKey(publicKeyBase64, "delegation");
        this.keyId = keyId;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.replayGuard = replayGuard;
        this.clock = clock;
    }

    public boolean isTrusted(ActorDelegation actor) {
        String assertion = RpcContext.getServerAttachment().getAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
        return DelegationAssertionVerifierSupport.verifyTrusted(
                actor == null ? null : actor.actorType(),
                actor == null ? null : actor.actorId(),
                actor == null ? null : actor.delegatorId(),
                assertion,
                publicKey,
                keyId,
                null,
                null,
                expectedIssuer,
                expectedAudience,
                replayGuard,
                clock,
                false,
                true);
    }
}

package com.ulticode.notification.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalDelegationAssertionVerifierTest {

    private static final String KEY_ID = "admin-delegation-v1";
    private static final String ACTOR_ID = "admin-1";
    private static final String AUDIENCE = "backend-notification";

    private KeyPair keyPair;
    private InternalDelegationAssertionVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = rsaKeyPair();
        verifier = new InternalDelegationAssertionVerifier(
                encode(keyPair.getPublic()),
                KEY_ID,
                DelegationAssertionContract.ISSUER,
                AUDIENCE,
                (audience, jti, ttl) -> true,
                java.time.Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        RpcContext.getServerAttachment().removeAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
    }

    @Test
    void acceptsFreshSignedRs256AssertionBoundToActor() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), AUDIENCE);

        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isTrue();
    }

    @Test
    void rejectsWrongAudienceExpiredAndBootstrapAssertions() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), "backend-app");
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, Instant.now().minusSeconds(1), AUDIENCE);
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), AUDIENCE, true);
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();
    }

    @Test
    void rejectsLongLivedAndNonSelfDelegation() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(120), AUDIENCE);
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), AUDIENCE);
        assertThat(verifier.isTrusted(new ActorDelegation(
                "ADMIN", ACTOR_ID, "different-delegator", "test"))).isFalse();
    }

    private void putAssertion(String subject, Instant expiresAt, String audience) {
        putAssertion(subject, expiresAt, audience, false);
    }

    private void putAssertion(String subject, Instant expiresAt, String audience, boolean bootstrap) {
        Instant issuedAt = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, "ADMIN")
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
        if (bootstrap) {
            builder.claim(DelegationAssertionContract.BOOTSTRAP_CLAIM, true);
        }
        String assertion = builder.header().keyId(KEY_ID).and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        RpcContext.getServerAttachment().setAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY, assertion);
    }

    private static ActorDelegation actor(String type, String id) {
        return new ActorDelegation(type, id, id, "test");
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String encode(java.security.PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}

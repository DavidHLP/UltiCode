package com.ulticode.submission.security;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Jwts;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalDelegationAssertionVerifierTest {

    private static final String KEY_ID = "admin-delegation-v1";
    private static final String ACTOR_ID = "admin-1";
    private static final String AUDIENCE = "backend-submission";
    private static final Instant NOW = Instant.now();

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
                Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        RpcContext.getServerAttachment().removeAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
    }

    @Test
    void acceptsFreshAssertionBoundToAdminActorAndOwnerAudience() {
        putAssertion(ACTOR_ID, NOW.plusSeconds(20), AUDIENCE, false);

        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isTrue();
    }

    @Test
    void rejectsWrongAudienceExpiredBootstrapAndNonAdminAssertions() {
        putAssertion(ACTOR_ID, NOW.plusSeconds(20), "backend-app", false);
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, NOW.minusSeconds(1), AUDIENCE, false);
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, NOW.plusSeconds(20), AUDIENCE, true);
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, NOW.plusSeconds(20), AUDIENCE, false);
        assertThat(verifier.isTrusted(actor("USER", ACTOR_ID))).isFalse();
    }

    @Test
    void rejectsLongLivedAndNonSelfDelegation() {
        putAssertion(ACTOR_ID, NOW.plusSeconds(120), AUDIENCE, false);
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, NOW.plusSeconds(20), AUDIENCE, false);
        assertThat(verifier.isTrusted(
                new ActorDelegation("ADMIN", ACTOR_ID, "different-delegator", "test"))).isFalse();
    }

    @Test
    void rejectsMissingAssertion() {
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();
    }

    private void putAssertion(String subject, Instant expiresAt, String audience, boolean bootstrap) {
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, "ADMIN")
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add(audience).and()
                .issuedAt(Date.from(NOW))
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

package com.ulticode.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalDelegationAssertionVerifierTest {

    private static final String KEY_ID = "admin-delegation-v1";
    private static final String BOOTSTRAP_KEY_ID = "bootstrap-delegation-v1";
    private static final String ACTOR_ID = "admin-1";

    private KeyPair keyPair;
    private KeyPair bootstrapKeyPair;
    private InternalDelegationAssertionVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = rsaKeyPair();
        bootstrapKeyPair = rsaKeyPair();
        verifier = new InternalDelegationAssertionVerifier(
                encode(keyPair.getPublic()),
                KEY_ID,
                encode(bootstrapKeyPair.getPublic()),
                BOOTSTRAP_KEY_ID,
                DelegationAssertionContract.ISSUER,
                "backend-auth",
                (audience, jti, ttl) -> true,
                java.time.Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        RpcContext.getServerAttachment().removeAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
    }

    @Test
    void acceptsFreshAssertionBoundToAuthAudienceAndActor() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), "backend-auth");

        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isTrue();
    }

    @Test
    void rejectsAssertionIssuedForAnotherOwner() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), "backend-app");

        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();
    }

    @Test
    void rejectsNonAdminActorEvenWithFreshAssertion() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), "backend-auth", "USER");

        assertThat(verifier.isTrusted(actor("USER", ACTOR_ID))).isFalse();
    }

    @Test
    void acceptsScopedBootstrapAssertionOnlyForBootstrapActor() {
        putBootstrapAssertion();

        assertThat(verifier.isTrusted(new ActorDelegation(
                "BOOTSTRAP", "bootstrap", "bootstrap", "one-shot"))).isTrue();
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();
    }

    @Test
    void rejectsMissingExpiredAndNonSelfDelegation() {
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, Instant.now().minusSeconds(1), "backend-auth");
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), "backend-auth");
        assertThat(verifier.isTrusted(new ActorDelegation(
                "ADMIN", ACTOR_ID, "different-admin", "test"))).isFalse();
    }

    private void putAssertion(String subject, Instant expiresAt, String audience) {
        putAssertion(subject, expiresAt, audience, "ADMIN");
    }

    private void putAssertion(String subject, Instant expiresAt, String audience, String actorType) {
        Instant issuedAt = Instant.now();
        String assertion = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, actorType)
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .header().keyId(KEY_ID).and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        RpcContext.getServerAttachment().setAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY, assertion);
    }

    private void putBootstrapAssertion() {
        Instant issuedAt = Instant.now();
        String assertion = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("bootstrap")
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, "BOOTSTRAP")
                .claim(DelegationAssertionContract.BOOTSTRAP_CLAIM, true)
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add("backend-auth").and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(20)))
                .header().keyId(BOOTSTRAP_KEY_ID).and()
                .signWith(bootstrapKeyPair.getPrivate(), Jwts.SIG.RS256)
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

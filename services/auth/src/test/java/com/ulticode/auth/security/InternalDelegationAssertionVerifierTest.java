package com.ulticode.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalDelegationAssertionVerifierTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final String BOOTSTRAP_SECRET = "bootstrap-secret-012345678901234567";
    private static final String ACTOR_ID = "admin-1";

    private InternalDelegationAssertionVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new InternalDelegationAssertionVerifier(
                SECRET, BOOTSTRAP_SECRET, DelegationAssertionContract.ISSUER, "backend-auth");
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
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String assertion = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, actorType)
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        RpcContext.getServerAttachment().setAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY, assertion);
    }

    private void putBootstrapAssertion() {
        Instant issuedAt = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(BOOTSTRAP_SECRET.getBytes(StandardCharsets.UTF_8));
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
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        RpcContext.getServerAttachment().setAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY, assertion);
    }

    private static ActorDelegation actor(String type, String id) {
        return new ActorDelegation(type, id, id, "test");
    }
}

package com.ulticode.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.common.command.ActorDelegation;
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
    private static final String ACTOR_ID = "admin-1";

    private InternalDelegationAssertionVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new InternalDelegationAssertionVerifier(
                SECRET,
                DelegationAssertionContract.ISSUER,
                DelegationAssertionContract.AUDIENCE);
    }

    @AfterEach
    void tearDown() {
        RpcContext.getServerAttachment().removeAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY);
    }

    @Test
    void acceptsFreshSignedAssertionBoundToActor() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20));

        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isTrue();
    }

    @Test
    void rejectsMissingExpiredOrMismatchedAssertion() {
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, Instant.now().minusSeconds(1));
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion("other-admin", Instant.now().plusSeconds(20));
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();
    }

    @Test
    void rejectsLongLivedAndNonSelfDelegation() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(120));
        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();

        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20));
        assertThat(verifier.isTrusted(new ActorDelegation(
                "ADMIN", ACTOR_ID, "different-delegator", "test"))).isFalse();
    }
    @Test
    void rejectsAssertionIssuedForDifferentOwner() {
        putAssertion(ACTOR_ID, Instant.now().plusSeconds(20), "backend-notification");

        assertThat(verifier.isTrusted(actor("ADMIN", ACTOR_ID))).isFalse();
    }

    private void putAssertion(String subject, Instant expiresAt) {
        putAssertion(subject, expiresAt, DelegationAssertionContract.AUDIENCE);
    }

    private void putAssertion(String subject, Instant expiresAt, String audience) {
        Instant issuedAt = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String assertion = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, "ADMIN")
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        RpcContext.getServerAttachment().setAttachment(
                DelegationAssertionContract.ATTACHMENT_KEY, assertion);
    }

    private static ActorDelegation actor(String type, String id) {
        return new ActorDelegation(type, id, id, "test");
    }
}

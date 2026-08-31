package com.ulticode.websecurity.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ulticode.common.security.DelegationAssertionContract;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DelegationAssertionVerifierSupportTest {

    private static final String KEY_ID = "admin-delegation-v1";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void validatesRs256HeaderAndBoundClaims() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        String assertion = assertion(keyPair, KEY_ID, NOW.plusSeconds(30), NOW);

        Claims claims = DelegationAssertionVerifierSupport.verify(
                assertion,
                keyPair.getPublic(),
                KEY_ID,
                DelegationAssertionContract.ISSUER,
                "backend-app",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(claims.getSubject()).isEqualTo("admin-1");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void rejectsWrongKidFutureIssuedAtAndExcessiveLifetime() throws Exception {
        KeyPair keyPair = rsaKeyPair();

        assertThatThrownBy(() -> DelegationAssertionVerifierSupport.verify(
                assertion(keyPair, "other-key", NOW.plusSeconds(30), NOW),
                keyPair.getPublic(), KEY_ID, DelegationAssertionContract.ISSUER,
                "backend-app", Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("kid");
        assertThatThrownBy(() -> DelegationAssertionVerifierSupport.verify(
                assertion(keyPair, KEY_ID, NOW.plusSeconds(30), NOW.plusSeconds(10)),
                keyPair.getPublic(), KEY_ID, DelegationAssertionContract.ISSUER,
                "backend-app", Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("lifetime");
        assertThatThrownBy(() -> DelegationAssertionVerifierSupport.verify(
                assertion(keyPair, KEY_ID, NOW.plusSeconds(120), NOW),
                keyPair.getPublic(), KEY_ID, DelegationAssertionContract.ISSUER,
                "backend-app", Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("lifetime");
    }

    @Test
    void appliesSharedActorBindingAndReplayPolicy() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        String assertion = assertion(keyPair, KEY_ID, NOW.plusSeconds(30), NOW);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThat(DelegationAssertionVerifierSupport.verifyTrusted(
                "ADMIN", "admin-1", "admin-1", assertion,
                keyPair.getPublic(), KEY_ID, null, null,
                DelegationAssertionContract.ISSUER, "backend-app",
                (audience, jti, ttl) -> true, clock, false, true)).isTrue();
        assertThat(DelegationAssertionVerifierSupport.verifyTrusted(
                "ADMIN", "admin-1", "other-admin", assertion,
                keyPair.getPublic(), KEY_ID, null, null,
                DelegationAssertionContract.ISSUER, "backend-app",
                (audience, jti, ttl) -> true, clock, false, true)).isFalse();
        assertThat(DelegationAssertionVerifierSupport.verifyTrusted(
                "ADMIN", "admin-1", "admin-1", assertion,
                keyPair.getPublic(), KEY_ID, null, null,
                DelegationAssertionContract.ISSUER, "backend-app",
                (audience, jti, ttl) -> false, clock, false, true)).isFalse();
    }

    @Test
    void rejectsWrongIssuerSignatureAndAlgorithm() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        String valid = assertion(keyPair, KEY_ID, NOW.plusSeconds(30), NOW);

        assertThatThrownBy(() -> DelegationAssertionVerifierSupport.verify(
                valid, keyPair.getPublic(), KEY_ID, "wrong-issuer", "backend-app", clock))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> DelegationAssertionVerifierSupport.verify(
                valid, rsaKeyPair().getPublic(), KEY_ID,
                DelegationAssertionContract.ISSUER, "backend-app", clock))
                .isInstanceOf(RuntimeException.class);

        String pssAssertion = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("admin-1")
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, "ADMIN")
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add("backend-app").and()
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(30)))
                .header().keyId(KEY_ID).and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.PS256)
                .compact();
        assertThatThrownBy(() -> DelegationAssertionVerifierSupport.verify(
                pssAssertion, keyPair.getPublic(), KEY_ID,
                DelegationAssertionContract.ISSUER, "backend-app", clock))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("algorithm");
    }

    @Test
    void bootstrapUsesTheBootstrapKeyAndClaim() throws Exception {
        KeyPair normalKey = rsaKeyPair();
        KeyPair bootstrapKey = rsaKeyPair();
        String assertion = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("bootstrap")
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, "BOOTSTRAP")
                .claim(DelegationAssertionContract.BOOTSTRAP_CLAIM, true)
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add("backend-auth").and()
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(30)))
                .header().keyId("bootstrap-key").and()
                .signWith(bootstrapKey.getPrivate(), Jwts.SIG.RS256)
                .compact();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThat(DelegationAssertionVerifierSupport.verifyTrusted(
                "BOOTSTRAP", "bootstrap", "bootstrap", assertion,
                normalKey.getPublic(), KEY_ID,
                bootstrapKey.getPublic(), "bootstrap-key",
                DelegationAssertionContract.ISSUER, "backend-auth",
                (audience, jti, ttl) -> true, clock, true, true)).isTrue();
        assertThat(DelegationAssertionVerifierSupport.verifyTrusted(
                "BOOTSTRAP", "bootstrap", "bootstrap", assertion,
                normalKey.getPublic(), KEY_ID,
                normalKey.getPublic(), "bootstrap-key",
                DelegationAssertionContract.ISSUER, "backend-auth",
                (audience, jti, ttl) -> true, clock, true, true)).isFalse();
    }

    private static String assertion(KeyPair keyPair, String keyId, Instant expiresAt, Instant issuedAt) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("admin-1")
                .claim(DelegationAssertionContract.ACTOR_SERVICE_CLAIM, "backend-admin")
                .claim(DelegationAssertionContract.ACTOR_TYPE_CLAIM, "ADMIN")
                .issuer(DelegationAssertionContract.ISSUER)
                .audience().add("backend-app").and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .header().keyId(keyId).and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}

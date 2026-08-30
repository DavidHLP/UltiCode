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

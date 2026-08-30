package com.ulticode.websecurity.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceServerJwtVerifierTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-testing";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private JwksPublicKeyProvider jwksProvider;
    private SecretKey hmacKey;
    private KeyPair rsaKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        jwksProvider = mock(JwksPublicKeyProvider.class);
        hmacKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        rsaKeyPair = generator.generateKeyPair();
    }

    @Test
    void verifiesHs256AndMapsTrustedAuthorityClaims() {
        AccessTokenClaims claims = verifier(Set.of("HS256", "RS256")).verify(hmacToken("access", NOW));

        assertThat(claims).isEqualTo(new AccessTokenClaims("user-1", "alice", "USER"));
    }

    @Test
    void verifiesRs256ByKid() {
        when(jwksProvider.getKey("kid-1")).thenReturn((RSAPublicKey) rsaKeyPair.getPublic());

        AccessTokenClaims claims = verifier(Set.of("RS256")).verify(rsaToken("kid-1"));

        assertThat(claims.userId()).isEqualTo("user-1");
    }

    @Test
    void rejectsAlgorithmDowngradeOutsideConfiguredAllowlist() {
        assertThatThrownBy(() -> verifier(Set.of("RS256")).verify(hmacToken("access", NOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("algorithm");
    }

    @Test
    void rejectsRs256WithoutKidOrTrustedKey() {
        assertThatThrownBy(() -> verifier(Set.of("RS256")).verify(rsaToken(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kid");
        assertThatThrownBy(() -> verifier(Set.of("RS256")).verify(rsaToken("unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted key");
    }

    @Test
    void rejectsWrongIssuerAudienceAndRefreshType() {
        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(hmacToken("access", NOW, "evil", "ulticode-api")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(hmacToken("access", NOW, "ulticode-auth", "evil")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(hmacToken("refresh", NOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsMissingExpirationIssuedAtAndFutureIssuedAt() {
        String missingExpiration = Jwts.builder()
                .subject("user-1").claim("username", "alice").claim("role", "USER")
                .issuer("ulticode-auth").audience().add("ulticode-api").and()
                .issuedAt(Date.from(NOW)).signWith(hmacKey, Jwts.SIG.HS256).compact();
        String missingIssuedAt = Jwts.builder()
                .subject("user-1").claim("username", "alice").claim("role", "USER")
                .issuer("ulticode-auth").audience().add("ulticode-api").and()
                .expiration(Date.from(NOW.plusSeconds(3600))).signWith(hmacKey, Jwts.SIG.HS256).compact();

        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(missingExpiration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(missingIssuedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(hmacToken("access", NOW.plusSeconds(120))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issued-at");
    }

    @Test
    void rejectsInvalidHeaderTypeAndAuthorityShape() {
        String wrongHeaderType = Jwts.builder()
                .header().type("refresh+jwt").and()
                .subject("user-1").claim("username", "alice").claim("role", "USER")
                .issuer("ulticode-auth").audience().add("ulticode-api").and()
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(hmacKey, Jwts.SIG.HS256).compact();
        String badRole = Jwts.builder()
                .subject("user-1").claim("username", "alice").claim("role", "admin;ROLE_SUPER_ADMIN")
                .issuer("ulticode-auth").audience().add("ulticode-api").and()
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(hmacKey, Jwts.SIG.HS256).compact();

        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(wrongHeaderType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header type");
        assertThatThrownBy(() -> verifier(Set.of("HS256")).verify(badRole))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role");
    }

    private ResourceServerJwtVerifier verifier(Set<String> algorithms) {
        return new ResourceServerJwtVerifier(
                jwksProvider,
                SECRET,
                "ulticode-auth",
                "ulticode-api",
                algorithms,
                Clock.fixed(NOW, ZoneOffset.UTC),
                30);
    }

    private String hmacToken(String type, Instant issuedAt) {
        return hmacToken(type, issuedAt, "ulticode-auth", "ulticode-api");
    }

    private String hmacToken(String type, Instant issuedAt, String issuer, String audience) {
        return Jwts.builder()
                .subject("user-1")
                .claim("username", "alice")
                .claim("role", "USER")
                .claim("type", type)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(hmacKey, Jwts.SIG.HS256)
                .compact();
    }

    private String rsaToken(String kid) {
        var builder = Jwts.builder()
                .subject("user-1")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(3600)));
        if (kid != null) {
            builder.header().keyId(kid).and();
        }
        return builder.signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }
}

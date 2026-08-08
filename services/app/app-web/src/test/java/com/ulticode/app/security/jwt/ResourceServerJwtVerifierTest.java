package com.ulticode.app.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ResourceServerJwtVerifier (backend-app)")
class ResourceServerJwtVerifierTest {

    private ResourceServerJwtVerifier verifier;
    private JwksPublicKeyProvider jwksProvider;
    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-testing";
    private SecretKey key;
    private KeyPair rsaKeyPair;
    private String rsaKid;

    @BeforeEach
    void setUp() {
        jwksProvider = org.mockito.Mockito.mock(JwksPublicKeyProvider.class);
        // Default: JWKS returns null (no RS256 keys available)
        org.mockito.Mockito.when(jwksProvider.getKey(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        verifier = new ResourceServerJwtVerifier(jwksProvider);
        ReflectionTestUtils.setField(verifier, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(verifier, "expectedIssuer", "ulticode-auth");
        ReflectionTestUtils.setField(verifier, "expectedAudience", "ulticode-api");
        key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        // Generate a test RSA keypair for RS256 tests
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            rsaKeyPair = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        rsaKid = "test-rsa-kid";
    }

    @Test
    @DisplayName("successfully verifies valid HS256 access token offline")
    void verifiesValidAccessToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        Claims claims = verifier.verifyAndParse(token);

        assertThat(verifier.getUserId(claims)).isEqualTo("user-123");
        assertThat(verifier.getUsername(claims)).isEqualTo("alice");
        assertThat(verifier.getRole(claims)).isEqualTo("USER");
    }

    @Test
    @DisplayName("successfully verifies valid RS256 access token when JWKS has the key")
    void verifiesRs256Token() {
        // Mock JWKS to return our test RSA public key
        org.mockito.Mockito.when(jwksProvider.getKey(rsaKid))
                .thenReturn((RSAPublicKey) rsaKeyPair.getPublic());

        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .issuedAt(now)
                .expiration(expiry)
                .header().keyId(rsaKid).and()
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        Claims claims = verifier.verifyAndParse(token);
        assertThat(verifier.getUserId(claims)).isEqualTo("user-123");
        assertThat(verifier.getRole(claims)).isEqualTo("USER");
    }

    @Test
    @DisplayName("REJECTS RS256 token when JWKS has no key for kid (fail-closed, no HS256 fallback)")
    void rejectsRs256WhenJwksMissesKid() {
        // JWKS returns null for this kid (default mock)
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .issuedAt(now)
                .expiration(expiry)
                .header().keyId("unknown-kid").and()
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no JWKS public key for kid=unknown-kid");
    }

    @Test
    @DisplayName("REJECTS RS256 token without kid (fail-closed)")
    void rejectsRs256WithoutKid() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no JWKS public key for kid=null");
    }

    @Test
    @DisplayName("REJECTS RS256 token whose signature does not match the JWKS key")
    void rejectsRs256WhenSignatureDoesNotMatchJwksKey() throws Exception {
        org.mockito.Mockito.when(jwksProvider.getKey(rsaKid))
                .thenReturn((RSAPublicKey) rsaKeyPair.getPublic());
        KeyPair wrongKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .issuedAt(now)
                .expiration(expiry)
                .header().keyId(rsaKid).and()
                .signWith(wrongKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("REJECTS tokens with unsupported algorithm (e.g. none)")
    void rejectsUnsupportedAlgorithm() {
        // Build a token header that claims alg=none (unsigned) — should be rejected
        // We craft a minimal unsigned JWT manually
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"user-123\",\"iss\":\"ulticode-auth\"}".getBytes());
        String unsignedToken = header + "." + payload + ".";

        assertThatThrownBy(() -> verifier.verifyAndParse(unsignedToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported JWT algorithm");
    }

    @Test
    @DisplayName("rejects refresh tokens when presented as access token")
    void rejectsRefreshToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String refreshToken = Jwts.builder()
                .subject("user-123")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refresh tokens cannot be used");
    }

    @Test
    @DisplayName("rejects expired tokens offline")
    void rejectsExpiredToken() {
        Date past = new Date(System.currentTimeMillis() - 60_000);

        String expiredToken = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .expiration(past)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(expiredToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("rejects tokens whose issuer does not match jwt.expected-issuer")
    void rejectsWrongIssuer() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("evil-issuer")
                .audience().add("ulticode-api").and()
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("rejects tokens that omit the issuer claim")
    void rejectsMissingIssuer() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .audience().add("ulticode-api").and()
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("rejects tokens whose audience does not match jwt.expected-audience")
    void rejectsWrongAudience() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("evil-audience").and()
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("rejects access tokens that omit the audience claim")
    void rejectsMissingAudience() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(Exception.class);
    }
}

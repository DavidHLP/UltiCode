package com.ulticode.admin.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ResourceServerJwtVerifier (backend-admin)")
class ResourceServerJwtVerifierTest {

    private ResourceServerJwtVerifier verifier;
    private JwksPublicKeyProvider jwksProvider;
    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-testing";
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwksProvider = org.mockito.Mockito.mock(JwksPublicKeyProvider.class);
        org.mockito.Mockito.when(jwksProvider.getKey(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        verifier = new ResourceServerJwtVerifier(jwksProvider);
        ReflectionTestUtils.setField(verifier, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(verifier, "expectedIssuer", "ulticode-auth");
        key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("successfully verifies valid Auth-issued access token offline")
    void verifiesValidAccessToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String token = Jwts.builder()
                .subject("admin-123")
                .claim("username", "admin")
                .claim("role", "ADMIN")
                .issuer("ulticode-auth")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();

        Claims claims = verifier.verifyAndParse(token);

        assertThat(verifier.getUserId(claims)).isEqualTo("admin-123");
        assertThat(verifier.getUsername(claims)).isEqualTo("admin");
        assertThat(verifier.getRole(claims)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("rejects refresh tokens when presented as access token")
    void rejectsRefreshToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String refreshToken = Jwts.builder()
                .subject("admin-123")
                .issuer("ulticode-auth")
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
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
                .subject("admin-123")
                .claim("username", "admin")
                .claim("role", "ADMIN")
                .issuer("ulticode-auth")
                .expiration(past)
                .signWith(key)
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
                .subject("admin-123")
                .claim("username", "admin")
                .claim("role", "ADMIN")
                .issuer("evil-issuer")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
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
                .subject("admin-123")
                .claim("username", "admin")
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(token))
                .isInstanceOf(Exception.class);
    }
}

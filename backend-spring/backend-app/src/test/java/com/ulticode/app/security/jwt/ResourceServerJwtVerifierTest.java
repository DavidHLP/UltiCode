package com.ulticode.app.security.jwt;

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

@DisplayName("ResourceServerJwtVerifier (backend-app)")
class ResourceServerJwtVerifierTest {

    private ResourceServerJwtVerifier verifier;
    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-testing";
    private SecretKey key;

    @BeforeEach
    void setUp() {
        verifier = new ResourceServerJwtVerifier();
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
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();

        Claims claims = verifier.verifyAndParse(token);

        assertThat(verifier.getUserId(claims)).isEqualTo("user-123");
        assertThat(verifier.getUsername(claims)).isEqualTo("alice");
        assertThat(verifier.getRole(claims)).isEqualTo("USER");
    }

    @Test
    @DisplayName("rejects refresh tokens when presented as access token")
    void rejectsRefreshToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000);

        String refreshToken = Jwts.builder()
                .subject("user-123")
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
                .subject("user-123")
                .claim("username", "alice")
                .claim("role", "USER")
                .expiration(past)
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> verifier.verifyAndParse(expiredToken))
                .isInstanceOf(Exception.class);
    }
}

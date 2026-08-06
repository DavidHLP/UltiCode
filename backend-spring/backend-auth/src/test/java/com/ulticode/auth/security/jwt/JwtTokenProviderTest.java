package com.ulticode.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * <p>Covers the JWT plumbing extracted to backend-auth under P2-AUTH-001-B:
 * the same HS256 secret + claims shape that backend-legacy still issues,
 * so a token minted here is verifiable by backend-legacy and vice versa
 * (Strangler Fig dual-run contract). Also pins the secret-length and
 * token-expiration contract that the guide §7.3 hot path relies on.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-32-chars-or-more-yes!!!";

    private static JwtTokenProvider newProvider() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.getAccessToken().setExpiration(60_000L);
        properties.getRefreshToken().setExpiration(120_000L);
        RsaKeyManager rsa = new RsaKeyManager();
        return new JwtTokenProvider(properties, rsa);
    }

    @Nested
    @DisplayName("validateSecret")
    class ValidateSecret {

        @Test
        @DisplayName("accepts a secret >= 32 chars")
        void acceptsValidSecret() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret(SECRET);
            // no exception expected — validateSecret is @PostConstruct
            properties.validateSecret();
        }

        @Test
        @DisplayName("rejects a null secret")
        void rejectsNullSecret() {
            JwtProperties properties = new JwtProperties();
            assertThatThrownBy(() -> properties.validateSecret())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects a blank secret")
        void rejectsBlankSecret() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret("   ");
            assertThatThrownBy(() -> properties.validateSecret())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("rejects a secret shorter than 32 chars")
        void rejectsShortSecret() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret("short");
            assertThatThrownBy(() -> properties.validateSecret())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("sign and verify")
    class SignAndVerify {

        @Test
        @DisplayName("access token round-trips claims through the same provider")
        void accessTokenRoundTrip() {
            JwtTokenProvider provider = newProvider();
            String token = provider.generateAccessToken("user-1", "alice", "USER");

            assertThat(provider.validateToken(token)).isTrue();
            assertThat(provider.getUserIdFromToken(token)).isEqualTo("user-1");
            assertThat(provider.getUsernameFromToken(token)).isEqualTo("alice");
            assertThat(provider.getRoleFromToken(token)).isEqualTo("USER");
        }

        @Test
        @DisplayName("refresh token carries the type=refresh claim and is distinct from access")
        void refreshTokenShape() {
            JwtTokenProvider provider = newProvider();
            String refresh = provider.generateRefreshToken("user-2");

            assertThat(provider.validateToken(refresh)).isTrue();
            assertThat(provider.getUserIdFromRefreshToken(refresh)).isEqualTo("user-2");
            Claims claims = provider.parseToken(refresh);
            assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        }

        @Test
        @DisplayName("two providers sharing the same secret verify each other's tokens")
        void twoProvidersShareSecret() {
            JwtTokenProvider signer = newProvider();
            JwtTokenProvider verifier = newProvider();

            String token = signer.generateAccessToken("user-3", "bob", "ADMIN");
            assertThat(verifier.validateToken(token)).isTrue();
            assertThat(verifier.getUserIdFromToken(token)).isEqualTo("user-3");
            assertThat(verifier.getRoleFromToken(token)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("a token signed with a different secret is rejected")
        void differentSecretFailsToVerify() {
            JwtTokenProvider signer = newProvider();

            JwtProperties otherProps = new JwtProperties();
            otherProps.setSecret("other-secret-32-chars-or-more-yes!!!");
            otherProps.getAccessToken().setExpiration(60_000L);
            otherProps.getRefreshToken().setExpiration(120_000L);
            JwtTokenProvider verifier = new JwtTokenProvider(otherProps, new RsaKeyManager());

            String token = signer.generateAccessToken("user-4", "carol", "USER");
            assertThat(verifier.validateToken(token)).isFalse();
            assertThat(verifier.parseToken(token)).isNull();
        }

        @Test
        @DisplayName("access token carries the configured issuer claim")
        void accessTokenStampsIssuer() {
            JwtTokenProvider provider = newProvider();
            String token = provider.generateAccessToken("user-7", "alice", "USER");
            Claims claims = provider.parseToken(token);
            assertThat(claims.getIssuer()).isEqualTo("ulticode-auth");
        }

        @Test
        @DisplayName("refresh token carries the configured issuer claim")
        void refreshTokenStampsIssuer() {
            JwtTokenProvider provider = newProvider();
            String refresh = provider.generateRefreshToken("user-8");
            Claims claims = provider.parseToken(refresh);
            assertThat(claims.getIssuer()).isEqualTo("ulticode-auth");
        }

        @Test
        @DisplayName("access token carries the configured audience claim")
        void accessTokenStampsAudience() {
            JwtTokenProvider provider = newProvider();
            String token = provider.generateAccessToken("user-9", "alice", "USER");
            Claims claims = provider.parseToken(token);
            assertThat(claims.getAudience()).contains("ulticode-api");
        }
    }

    @Nested
    @DisplayName("expiration")
    class Expiration {

        @Test
        @DisplayName("a token with past expiration is detected and parseToken throws")
        void expiredTokenIsDetected() {
            JwtProperties props = new JwtProperties();
            props.setSecret(SECRET);
            props.getAccessToken().setExpiration(-1L);
            props.getRefreshToken().setExpiration(-1L);
            JwtTokenProvider provider = new JwtTokenProvider(props, new RsaKeyManager());

            String token = provider.generateAccessToken("user-5", "dave", "USER");
            assertThat(provider.isTokenExpired(token)).isTrue();
            assertThatThrownBy(() -> provider.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("issuedAt is set to the current time (at second resolution)")
        void issuedAtIsNow() {
            JwtTokenProvider provider = newProvider();
            long before = System.currentTimeMillis() / 1000L;
            String token = provider.generateAccessToken("user-6", "erin", "USER");
            long after = System.currentTimeMillis() / 1000L;

            Claims claims = provider.parseToken(token);
            assertThat(claims.getIssuedAt()).isNotNull();
            // jjwt writes issuedAt at second resolution; tolerate one-second skew.
            assertThat(claims.getIssuedAt().getTime() / 1000L).isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("algorithm pinning")
    class AlgorithmPinning {

        @Test
        @DisplayName("long secret (>= 48 chars) still signs as HS256, not HS384/HS512")
        void longSecretSignsAsHs256() {
            // A 64-char secret would trigger HS512 via jjwt auto-selection
            // unless the signer explicitly pins HS256.
            String longSecret = "a".repeat(64);
            JwtProperties props = new JwtProperties();
            props.setSecret(longSecret);
            props.getAccessToken().setExpiration(60_000L);
            props.getRefreshToken().setExpiration(120_000L);
            JwtTokenProvider provider = new JwtTokenProvider(props, new RsaKeyManager());

            String token = provider.generateAccessToken("user-10", "alice", "USER");

            // Decode header to verify alg is HS256
            String headerB64 = token.split("\\.")[0];
            String headerJson = new String(java.util.Base64.getUrlDecoder().decode(headerB64));
            assertThat(headerJson).contains("\"alg\":\"HS256\"");
            assertThat(headerJson).doesNotContain("HS384").doesNotContain("HS512");
        }
    }
}

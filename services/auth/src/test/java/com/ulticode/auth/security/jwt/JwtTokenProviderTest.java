package com.ulticode.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

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
        return newProvider(new RsaKeyManager());
    }

    private static JwtTokenProvider newProvider(RsaKeyManager rsa) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.getAccessToken().setExpiration(60_000L);
        properties.getRefreshToken().setExpiration(120_000L);
        return new JwtTokenProvider(properties, rsa);
    }

    private static RsaKeyManager newRsaKeyManager(String currentKeyB64, String previousKeyB64) {
        RsaKeyManager rsa = new RsaKeyManager();
        ReflectionTestUtils.setField(rsa, "rsaEnabled", true);
        ReflectionTestUtils.setField(rsa, "currentPrivateKeyB64", currentKeyB64);
        ReflectionTestUtils.setField(rsa, "previousPrivateKeyB64", previousKeyB64);
        rsa.init();
        return rsa;
    }

    private static String generateBase64Pkcs8() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    }

    private static String signedRsaAccessToken(PrivateKey privateKey, String kid) {
        var builder = Jwts.builder()
                .subject("rsa-user")
                .claim("username", "rsa-alice")
                .claim("role", "USER")
                .issuer("ulticode-auth")
                .audience().add("ulticode-api").and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L));
        if (kid != null) {
            builder.header().keyId(kid).and();
        }
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }
    private static String signedHmacToken(String issuer, String audience, String type) {
        var builder = Jwts.builder()
                .subject("hmac-user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L));
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (audience != null) {
            builder.audience().add(audience).and();
        }
        if (type != null) {
            builder.claim("type", type);
        }
        return builder.signWith(
                        Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
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

        @Test
        @DisplayName("rejects insecure cookies outside an explicit local profile")
        void rejectsInsecureCookiesWithoutLocalProfile() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret(SECRET);
            properties.getCookie().getAccessToken().setSecure(false);
            properties.getCookie().getRefreshToken().setSecure(false);
            MockEnvironment production = new MockEnvironment();
            production.setActiveProfiles("prod");
            properties.setEnvironment(production);

            assertThatThrownBy(properties::validateSecret)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Secure");
        }

        @Test
        @DisplayName("rejects insecure cookies when a production profile is mixed with dev")
        void rejectsInsecureCookiesInMixedProductionProfile() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret(SECRET);
            properties.getCookie().getAccessToken().setSecure(false);
            properties.getCookie().getRefreshToken().setSecure(false);
            MockEnvironment mixed = new MockEnvironment();
            mixed.setActiveProfiles("dev", "prod");
            properties.setEnvironment(mixed);

            assertThatThrownBy(properties::validateSecret)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Secure");
        }

        @Test
        @DisplayName("allows insecure cookies only in an explicit local profile")
        void allowsInsecureCookiesInDevProfile() {
            JwtProperties properties = new JwtProperties();
            properties.setSecret(SECRET);
            properties.getCookie().getAccessToken().setSecure(false);
            properties.getCookie().getRefreshToken().setSecure(false);
            MockEnvironment development = new MockEnvironment();
            development.setActiveProfiles("dev");
            properties.setEnvironment(development);

            properties.validateSecret();
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
    @DisplayName("RS256 verification")
    class RsaVerification {

        @Test
        @DisplayName("current RSA key signs and self-verifies an access token")
        void currentRsaKeyRoundTrips() throws Exception {
            RsaKeyManager rsa = newRsaKeyManager(generateBase64Pkcs8(), null);
            JwtTokenProvider provider = newProvider(rsa);

            String token = provider.generateAccessToken("rsa-user", "alice", "USER");

            String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
            assertThat(headerJson).contains("\"alg\":\"RS256\"");
            assertThat(headerJson).contains("\"kid\":\"" + rsa.getKeyId() + "\"");
            assertThat(provider.validateToken(token)).isTrue();
            assertThat(provider.getUserIdFromToken(token)).isEqualTo("rsa-user");
        }

        @Test
        @DisplayName("refresh tokens remain HS256 and retain their no-audience shape when RS256 is enabled")
        void refreshTokenCompatibilityWhenRsaEnabled() throws Exception {
            JwtTokenProvider provider = newProvider(newRsaKeyManager(generateBase64Pkcs8(), null));

            String refresh = provider.generateRefreshToken("refresh-user");
            Claims claims = provider.parseToken(refresh);

            assertThat(provider.validateToken(refresh)).isTrue();
            assertThat(claims.get("type", String.class)).isEqualTo("refresh");
            assertThat(claims.getAudience()).isNull();
        }

        @Test
        @DisplayName("previous RSA key remains verifiable during rotation overlap")
        void previousRsaKeyRemainsVerifiable() throws Exception {
            String currentKey = generateBase64Pkcs8();
            String previousKey = generateBase64Pkcs8();
            JwtTokenProvider previousSigner =
                    newProvider(newRsaKeyManager(previousKey, null));
            JwtTokenProvider rotatedVerifier =
                    newProvider(newRsaKeyManager(currentKey, previousKey));

            String token = previousSigner.generateAccessToken("previous-user", "alice", "USER");

            assertThat(rotatedVerifier.validateToken(token)).isTrue();
            assertThat(rotatedVerifier.getUserIdFromToken(token)).isEqualTo("previous-user");
        }

        @Test
        @DisplayName("missing or unknown RS256 kid is rejected without fallback")
        void missingOrUnknownKidFailsClosed() throws Exception {
            RsaKeyManager rsa = newRsaKeyManager(generateBase64Pkcs8(), null);
            JwtTokenProvider provider = newProvider(rsa);

            String missingKid = signedRsaAccessToken(rsa.getPrivateKey(), null);
            String unknownKid = signedRsaAccessToken(rsa.getPrivateKey(), "unknown-kid");

            assertThat(provider.parseToken(missingKid)).isNull();
            assertThat(provider.parseToken(unknownKid)).isNull();
            assertThat(provider.validateToken(missingKid)).isFalse();
            assertThat(provider.validateToken(unknownKid)).isFalse();
        }

        @Test
        @DisplayName("a bad RSA signature is rejected")
        void badRsaSignatureFails() throws Exception {
            RsaKeyManager rsa = newRsaKeyManager(generateBase64Pkcs8(), null);
            JwtTokenProvider provider = newProvider(rsa);
            String token = provider.generateAccessToken("rsa-user", "alice", "USER");
            String[] parts = token.split("\\.");
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            signature[0] ^= 1;
            String tampered = parts[0] + "." + parts[1] + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            assertThat(provider.parseToken(tampered)).isNull();
        }
    }

    @Nested
    @DisplayName("claim policy and failure paths")
    class ClaimPolicy {

        @Test
        @DisplayName("access tokens require the configured issuer and audience")
        void accessClaimsAreRequired() {
            JwtTokenProvider provider = newProvider();

            assertThat(provider.parseToken(signedHmacToken(null, "ulticode-api", null))).isNull();
            assertThat(provider.parseToken(signedHmacToken("ulticode-auth", null, null))).isNull();
            assertThat(provider.parseToken(signedHmacToken("other-issuer", "ulticode-api", null))).isNull();
        }

        @Test
        @DisplayName("refresh tokens retain type=refresh and reject an audience claim")
        void refreshShapeIsStrict() {
            JwtTokenProvider provider = newProvider();
            String refreshWithAudience = signedHmacToken("ulticode-auth", "ulticode-api", "refresh");

            assertThat(provider.parseToken(refreshWithAudience)).isNull();
        }

        @Test
        @DisplayName("unsupported algorithms are rejected before key verification")
        void unsupportedAlgorithmFailsClosed() {
            String longSecret = "a".repeat(64);
            String token = Jwts.builder()
                    .subject("user")
                    .issuer("ulticode-auth")
                    .audience().add("ulticode-api").and()
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60_000L))
                    .signWith(
                            Keys.hmacShaKeyFor(longSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            Jwts.SIG.HS384)
                    .compact();

            assertThat(newProvider().parseToken(token)).isNull();
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

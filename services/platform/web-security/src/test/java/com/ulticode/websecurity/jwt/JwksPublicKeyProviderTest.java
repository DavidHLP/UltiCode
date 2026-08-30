package com.ulticode.websecurity.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwksPublicKeyProviderTest {

    @Test
    void rejectsPlainHttpOutsideExclusiveLocalLoopback() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        MockEnvironment mixed = new MockEnvironment();
        mixed.setActiveProfiles("dev", "prod");
        MockEnvironment development = new MockEnvironment();
        development.setActiveProfiles("dev");

        assertThatThrownBy(() -> JwksPublicKeyProvider.validateJwksUri(
                "http://backend-auth:9101/auth/jwks", "backend-auth", production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> JwksPublicKeyProvider.validateJwksUri(
                "http://localhost:9101/auth/jwks", "localhost", mixed))
                .isInstanceOf(IllegalStateException.class);
        JwksPublicKeyProvider.validateJwksUri(
                "http://localhost:9101/auth/jwks", "localhost", development);
        JwksPublicKeyProvider.validateJwksUri(
                "https://backend-auth:9101/auth/jwks", "backend-auth", production);
    }

    @Test
    void rejectsNonAllowlistedHostAndWeakRsaKey() throws Exception {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> JwksPublicKeyProvider.validateJwksUri(
                "https://attacker.example/jwks", "backend-auth", production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowlisted");

        KeyPairGenerator weakGenerator = KeyPairGenerator.getInstance("RSA");
        weakGenerator.initialize(1024);
        assertThatThrownBy(() -> JwksPublicKeyProvider.parseKeys(jwks("weak", weakGenerator.generateKeyPair())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void unknownKidsDoNotAmplifyRefreshesBeforeTtl() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T04:00:00Z"));
        AtomicInteger loads = new AtomicInteger();
        String document = jwks("known", rsaKeyPair());
        JwksPublicKeyProvider provider = new JwksPublicKeyProvider(
                true, 60, 10, clock, () -> {
                    loads.incrementAndGet();
                    return document;
                });

        assertThat(provider.getKey("unknown-1")).isNull();
        assertThat(provider.getKey("unknown-2")).isNull();
        assertThat(provider.getKey("unknown-3")).isNull();
        assertThat(loads).hasValue(1);
    }

    @Test
    void refreshesRotatedKeysAfterTtl() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T04:00:00Z"));
        AtomicReference<String> document = new AtomicReference<>(jwks("old", rsaKeyPair()));
        JwksPublicKeyProvider provider = new JwksPublicKeyProvider(true, 60, 10, clock, document::get);

        assertThat(provider.getKey("old")).isNotNull();
        document.set(jwks("new", rsaKeyPair()));
        clock.advanceSeconds(61);

        assertThat(provider.getKey("new")).isNotNull();
        assertThat(provider.getKey("old")).isNull();
    }

    @Test
    void outageRetainsLastKnownKeyAndUsesRetryBackoff() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-30T04:00:00Z"));
        AtomicInteger loads = new AtomicInteger();
        String document = jwks("stable", rsaKeyPair());
        JwksPublicKeyProvider provider = new JwksPublicKeyProvider(
                true, 60, 10, clock, () -> {
                    if (loads.incrementAndGet() == 1) {
                        return document;
                    }
                    throw new IllegalStateException("outage");
                });

        assertThat(provider.getKey("stable")).isNotNull();
        clock.advanceSeconds(61);
        assertThat(provider.getKey("stable")).isNotNull();
        assertThat(provider.getKey("stable")).isNotNull();
        assertThat(loads).hasValue(2);
    }
    @Test
    void staticJwksIsLoadedWithoutNetworkConfiguration() throws Exception {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        KeyPair keyPair = rsaKeyPair();
        JwksPublicKeyProvider provider = new JwksPublicKeyProvider(
                true,
                "http://ignored.example/jwks",
                jwks("static", keyPair),
                60,
                10,
                "backend-auth",
                production,
                null);

        assertThat(provider.getKey("static")).isNotNull();
    }


    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwks(String kid, KeyPair keyPair) {
        RSAPublicKey key = (RSAPublicKey) keyPair.getPublic();
        String modulus = Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(key.getModulus().toByteArray()));
        String exponent = Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(key.getPublicExponent().toByteArray()));
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\""
                + kid + "\",\"n\":\"" + modulus + "\",\"e\":\"" + exponent + "\"}]}";
    }

    private static byte[] unsigned(byte[] value) {
        if (value.length > 1 && value[0] == 0) {
            return java.util.Arrays.copyOfRange(value, 1, value.length);
        }
        return value;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

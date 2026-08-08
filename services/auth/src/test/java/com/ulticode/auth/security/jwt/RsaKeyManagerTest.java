package com.ulticode.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link RsaKeyManager}.
 *
 * <p>Verifies the P3 production key-management contract:
 * <ul>
 *   <li>Deterministic kid: same key material → same kid on every replica</li>
 *   <li>N/N-1 rotation: JWKS publishes both current and previous keys</li>
 *   <li>Fail-fast: rsa.enabled=true without key material throws at startup</li>
 *   <li>Disabled mode: returns null key material and empty JWKS</li>
 * </ul>
 */
@DisplayName("RsaKeyManager")
class RsaKeyManagerTest {

    /** Generate a fresh 2048-bit RSA keypair and return its base64 PKCS8 encoding. */
    private static String generateBase64Pkcs8() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    }

    @Test
    @DisplayName("disabled by default: no key material and empty JWKS")
    void disabledByDefault() {
        RsaKeyManager mgr = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr, "rsaEnabled", false);
        mgr.init();

        assertThat(mgr.getPrivateKey()).isNull();
        assertThat(mgr.getKeyId()).isNull();
        assertThat(mgr.getPublicKey()).isNull();
        assertThat(mgr.toJwkSet()).containsKey("keys");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) mgr.toJwkSet().get("keys");
        assertThat(keys).isEmpty();
        assertThat(Arrays.stream(RsaKeyManager.class.getMethods())
                .map(Method::getName)
                .toList())
                .doesNotContain("getCurrentPrivateKeyB64", "getPreviousPrivateKeyB64",
                        "getCurrentKey", "getPreviousKey");
    }

    @Test
    @DisplayName("enabled without key: fails fast at init")
    void enabledWithoutKeyFailsFast() {
        RsaKeyManager mgr = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr, "currentPrivateKeyB64", "");

        assertThatThrownBy(mgr::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.rsa.private-key is not set");
    }

    @Test
    @DisplayName("loads current key from base64 PKCS8 and exposes private/public/kid")
    void loadsCurrentKey() throws Exception {
        String b64 = generateBase64Pkcs8();
        RsaKeyManager mgr = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr, "currentPrivateKeyB64", b64);
        mgr.init();

        assertThat(mgr.getPrivateKey()).isNotNull();
        assertThat(mgr.getPublicKey()).isNotNull();
        assertThat(mgr.getKeyId()).isNotBlank();
        assertThat(mgr.getPrivateKey().getEncoded())
                .isEqualTo(Base64.getDecoder().decode(b64));
    }

    @Test
    @DisplayName("deterministic kid: same key material produces same kid across instances")
    void deterministicKid() throws Exception {
        String b64 = generateBase64Pkcs8();

        RsaKeyManager mgr1 = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr1, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr1, "currentPrivateKeyB64", b64);
        mgr1.init();

        RsaKeyManager mgr2 = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr2, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr2, "currentPrivateKeyB64", b64);
        mgr2.init();

        assertThat(mgr1.getKeyId()).isEqualTo(mgr2.getKeyId());
    }

    @Test
    @DisplayName("different keys produce different kids")
    void differentKidsForDifferentKeys() throws Exception {
        String b64a = generateBase64Pkcs8();
        String b64b = generateBase64Pkcs8();

        RsaKeyManager mgr1 = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr1, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr1, "currentPrivateKeyB64", b64a);
        mgr1.init();

        RsaKeyManager mgr2 = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr2, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr2, "currentPrivateKeyB64", b64b);
        mgr2.init();

        assertThat(mgr1.getKeyId()).isNotEqualTo(mgr2.getKeyId());
    }

    @Test
    @DisplayName("JWKS publishes current key only when no previous key configured")
    void jwksCurrentOnly() throws Exception {
        String b64 = generateBase64Pkcs8();
        RsaKeyManager mgr = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr, "currentPrivateKeyB64", b64);
        mgr.init();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) mgr.toJwkSet().get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0)).containsEntry("kid", mgr.getKeyId());
        assertThat(keys.get(0)).containsEntry("alg", "RS256");
        assertThat(keys.get(0)).containsEntry("kty", "RSA");
    }

    @Test
    @DisplayName("JWKS publishes both current and previous keys for N/N-1 rotation")
    void jwksWithPreviousKey() throws Exception {
        String currentB64 = generateBase64Pkcs8();
        String previousB64 = generateBase64Pkcs8();
        RsaKeyManager mgr = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr, "currentPrivateKeyB64", currentB64);
        ReflectionTestUtils.setField(mgr, "previousPrivateKeyB64", previousB64);
        mgr.init();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) mgr.toJwkSet().get("keys");
        assertThat(keys).hasSize(2);
        assertThat(keys.get(0).get("kid")).isEqualTo(mgr.getKeyId());
        assertThat(keys.get(0).get("alg")).isEqualTo("RS256");
        assertThat(keys.get(0).get("kty")).isEqualTo("RSA");
        assertThat(keys.get(1).get("kid")).isNotEqualTo(mgr.getKeyId());
        assertThat(keys.get(1).get("kid")).isNotNull();
        assertThat(mgr.getPublicKey(mgr.getKeyId())).isEqualTo(mgr.getPublicKey());
        assertThat(mgr.getPublicKey((String) keys.get(1).get("kid"))).isNotNull();
        assertThat(mgr.getPublicKey("unknown-kid")).isNull();
        assertThat(mgr.getPublicKey((String) null)).isNull();
    }

    @Test
    @DisplayName("rejects invalid base64 key material")
    void rejectsInvalidKey() {
        RsaKeyManager mgr = new RsaKeyManager();
        ReflectionTestUtils.setField(mgr, "rsaEnabled", true);
        ReflectionTestUtils.setField(mgr, "currentPrivateKeyB64", "not-valid-base64-key-material");

        assertThatThrownBy(mgr::init)
                .isInstanceOf(IllegalStateException.class);
    }
}

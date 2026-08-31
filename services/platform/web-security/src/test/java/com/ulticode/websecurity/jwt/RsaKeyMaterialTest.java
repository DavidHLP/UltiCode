package com.ulticode.websecurity.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RsaKeyMaterialTest {

    @Test
    void loadsPkcs8PrivateAndX509PublicKeys() throws Exception {
        KeyPair keyPair = rsaKeyPair(2048);

        assertThat(RsaKeyMaterial.loadPrivateKey(encode(keyPair.getPrivate())).getAlgorithm())
                .isEqualTo("RSA");
        assertThat(RsaKeyMaterial.loadPublicKey(encode(keyPair.getPublic())).getModulus())
                .isEqualTo(((java.security.interfaces.RSAPublicKey) keyPair.getPublic()).getModulus());
        assertThat(RsaKeyMaterial.loadOptionalPublicKey(
                encode(keyPair.getPublic()), "delegation")).isNotNull();
        assertThat(RsaKeyMaterial.loadOptionalPublicKey("", "delegation")).isNull();
    }

    @Test
    void rejectsMissingMalformedAndWeakKeys() throws Exception {
        assertThatThrownBy(() -> RsaKeyMaterial.loadPublicKey(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> RsaKeyMaterial.loadPrivateKey("not-a-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid RSA private key");

        KeyPair weak = rsaKeyPair(1024);
        assertThatThrownBy(() -> RsaKeyMaterial.loadPublicKey(encode(weak.getPublic())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048");
    }

    private static KeyPair rsaKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static String encode(java.security.Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}

package com.ulticode.auth.security.jwt;

import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AUTH-COMP-006: RSA key pair manager for RS256 JWT signing.
 *
 * <p>Generates a single RSA 2048 key pair at startup with a random kid.
 * The public key is exposed via JWKS; the private key signs access tokens.
 * During the overlap period, HS256 verification continues to work so
 * existing tokens issued before the cutover remain valid.
 */
@Slf4j
@Getter
@Component
public class RsaKeyManager {

    @Value("${jwt.rsa.enabled:false}")
    private boolean rsaEnabled;

    @Value("${jwt.rsa.key-size:2048}")
    private int keySize;

    private KeyPair keyPair;
    private String keyId;

    @PostConstruct
    public void init() {
        if (!rsaEnabled) {
            log.info("RS256 JWT signing is disabled (jwt.rsa.enabled=false); Auth uses HS256 only");
            return;
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            this.keyPair = generator.generateKeyPair();
            this.keyId = UUID.randomUUID().toString();
            log.info("RSA key pair generated for RS256 JWT signing (kid={}, size={})", keyId, keySize);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

    public PrivateKey getPrivateKey() {
        if (!rsaEnabled || keyPair == null) {
            return null;
        }
        return keyPair.getPrivate();
    }

    public PublicKey getPublicKey() {
        if (!rsaEnabled || keyPair == null) {
            return null;
        }
        return keyPair.getPublic();
    }

    /**
     * Build a JWK Set (RFC 7517) representation containing the public key.
     *
     * @return a Map suitable for JSON serialization, or empty if RS256 is disabled.
     */
    public Map<String, Object> toJwkSet() {
        if (!rsaEnabled || keyPair == null) {
            return Map.of("keys", java.util.Collections.emptyList());
        }
        RSAPublicKey rsaPublic = (RSAPublicKey) keyPair.getPublic();
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPublic.getModulus().toByteArray()));
        jwk.put("e", java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPublic.getPublicExponent().toByteArray()));
        return Map.of("keys", java.util.List.of(jwk));
    }
}

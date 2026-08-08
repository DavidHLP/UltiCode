package com.ulticode.auth.security.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AUTH-COMP-006: RSA key pair manager for RS256 JWT signing.
 *
 * <p>Loads RSA private key material from configuration (base64-encoded PKCS8)
 * so that all Auth replicas in production share the same signing key. The key
 * ID is derived deterministically from the public key modulus (SHA-256 thumbprint),
 * guaranteeing that identical key material produces the same {@code kid} on every
 * replica — a prerequisite for JWKS-based key rotation.
 *
 * <p>Supports an optional {@code previous} key for N/N-1 rotation overlap: the
 * current key signs new tokens, while the previous key remains published in
 * JWKS so that resource servers can still verify tokens issued before rotation.
 *
 * <p>When {@code jwt.rsa.enabled=false} (default), Auth signs with HS256 only
 * and this manager exposes no key material.
 */
@Slf4j
@Component
public class RsaKeyManager {

    @Value("${jwt.rsa.enabled:false}")
    private boolean rsaEnabled;

    @Value("${jwt.rsa.private-key:}")
    private String currentPrivateKeyB64;

    @Value("${jwt.rsa.previous-private-key:}")
    private String previousPrivateKeyB64;

    /** Current signing key entry (never null when rsaEnabled). */
    private KeyEntry currentKey;

    /** Previous key entry for overlap verification (null when no previous key configured). */
    private KeyEntry previousKey;

    public boolean isRsaEnabled() {
        return rsaEnabled;
    }

    @PostConstruct
    public void init() {
        if (!rsaEnabled) {
            log.info("RS256 JWT signing is disabled (jwt.rsa.enabled=false); Auth uses HS256 only");
            return;
        }
        if (currentPrivateKeyB64 == null || currentPrivateKeyB64.isBlank()) {
            throw new IllegalStateException(
                    "jwt.rsa.enabled=true but jwt.rsa.private-key is not set. "
                            + "Provide a base64-encoded PKCS8 RSA private key via JWT_RSA_PRIVATE_KEY.");
        }
        this.currentKey = loadKeyEntry(currentPrivateKeyB64, "current");
        log.info("RSA current key loaded for RS256 JWT signing (kid={})", currentKey.kid);

        if (previousPrivateKeyB64 != null && !previousPrivateKeyB64.isBlank()) {
            this.previousKey = loadKeyEntry(previousPrivateKeyB64, "previous");
            log.info("RSA previous key loaded for overlap verification (kid={})", previousKey.kid);
        }
    }

    public PrivateKey getPrivateKey() {
        return currentKey != null ? currentKey.privateKey : null;
    }

    public String getKeyId() {
        return currentKey != null ? currentKey.kid : null;
    }

    public PublicKey getPublicKey() {
        return currentKey != null ? currentKey.publicKey : null;
    }

    /**
     * Resolve a public key from the current/previous rotation ring by {@code kid}.
     * The private key and internal {@link KeyEntry} remain inaccessible to callers.
     *
     * @param keyId the JWT {@code kid} header value
     * @return the matching public key, or {@code null} when the id is absent/unknown
     */
    public PublicKey getPublicKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return null;
        }
        if (currentKey != null && keyId.equals(currentKey.kid)) {
            return currentKey.publicKey;
        }
        if (previousKey != null && keyId.equals(previousKey.kid)) {
            return previousKey.publicKey;
        }
        return null;
    }

    /**
     * Build a JWK Set (RFC 7517) containing the current and (if configured)
     * previous public keys for JWKS endpoint publication.
     *
     * @return a Map suitable for JSON serialization, or empty if RS256 is disabled.
     */
    public Map<String, Object> toJwkSet() {
        if (!rsaEnabled || currentKey == null) {
            return Map.of("keys", List.of());
        }
        List<Map<String, Object>> keys = new ArrayList<>();
        keys.add(toJwk(currentKey));
        if (previousKey != null) {
            keys.add(toJwk(previousKey));
        }
        return Map.of("keys", keys);
    }

    // ---- internal helpers ----

    private KeyEntry loadKeyEntry(String base64Pkcs8, String label) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Pkcs8.trim());
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            RSAPublicKey publicKey = derivePublicKey(privateKey);
            String kid = computeKid(publicKey);
            return new KeyEntry(privateKey, publicKey, kid);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + label + " RSA key: " + e.getMessage(), e);
        }
    }

    /**
     * Derive an RSAPublicKey from an RSA PrivateKey by extracting modulus
     * and public exponent.
     */
    private RSAPublicKey derivePublicKey(PrivateKey privateKey) {
        java.security.interfaces.RSAPrivateCrtKey crtKey =
                (java.security.interfaces.RSAPrivateCrtKey) privateKey;
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(
                            crtKey.getModulus(), crtKey.getPublicExponent()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive RSA public key", e);
        }
    }

    /**
     * Compute a deterministic key ID from the RSA public key modulus using
     * SHA-256 thumbprint (RFC 7638 §3.1). Same key material → same kid on every replica.
     */
    private String computeKid(RSAPublicKey publicKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(publicKey.getModulus().toByteArray());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute RSA key ID", e);
        }
    }

    private Map<String, Object> toJwk(KeyEntry entry) {
        RSAPublicKey rsaPublic = (RSAPublicKey) entry.publicKey;
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", entry.kid);
        jwk.put("n", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPublic.getModulus().toByteArray()));
        jwk.put("e", Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rsaPublic.getPublicExponent().toByteArray()));
        return jwk;
    }

    /** Immutable holder for a loaded RSA key pair and its derived kid. */
    private record KeyEntry(PrivateKey privateKey, PublicKey publicKey, String kid) {}
}

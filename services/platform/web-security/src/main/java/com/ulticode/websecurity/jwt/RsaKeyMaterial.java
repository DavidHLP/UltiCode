package com.ulticode.websecurity.jwt;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Loads RSA key material without exposing encoded values in exceptions or logs. */
public final class RsaKeyMaterial {

    private static final int MIN_RSA_MODULUS_BITS = 2048;

    private RsaKeyMaterial() {
    }

    public static PrivateKey loadPrivateKey(String encoded) {
        try {
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decode(encoded, "private")));
            if (!(key instanceof RSAPrivateKey rsaKey)
                    || rsaKey.getModulus().bitLength() < MIN_RSA_MODULUS_BITS) {
                throw new IllegalArgumentException("RSA private key must be at least 2048 bits");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RSA private key", exception);
        }
    }

    public static RSAPublicKey loadPublicKey(String encoded) {
        try {
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decode(encoded, "public")));
            if (key.getModulus().bitLength() < MIN_RSA_MODULUS_BITS) {
                throw new IllegalArgumentException("RSA public key must be at least 2048 bits");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid RSA public key", exception);
        }
    }

    public static RSAPublicKey loadOptionalPublicKey(String encoded, String label) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return loadPublicKey(encoded);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid " + label + " public key", exception);
        }
    }

    private static byte[] decode(String encoded, String label) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("RSA " + label + " key is required");
        }
        String normalized = encoded
                .replace("-----BEGIN PRIVATE KEY-----", "") // gitleaks:allow: PEM framing marker only; no private-key body
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid RSA " + label + " key encoding", exception);
        }
    }
}

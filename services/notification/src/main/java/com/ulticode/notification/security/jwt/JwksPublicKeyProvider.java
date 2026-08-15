package com.ulticode.notification.security.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AUTH-COMP-007: fetches RSA public keys from the Auth service's JWKS endpoint
 * with a TTL cache and {@code kid}-based lookup.
 *
 * <p>The provider caches all keys from {@code /auth/jwks} for a configurable
 * TTL (default 15 minutes). When a token's {@code kid} is not found in the
 * cache, a single forced refresh is attempted before giving up.
 *
 * <p>If {@code jwt.rsa.enabled=false} or no matching key is available in the
 * cache after the refresh attempt, {@link #getKey(String)} returns {@code null};
 * the RS256 token is then rejected by the resource verifier. A failed refresh
 * never enables HS256 fallback for an RS256 token.
 */
@Slf4j
@Component
public class JwksPublicKeyProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${jwt.rsa.enabled:false}")
    private boolean rsaEnabled;

    @Value("${jwt.jwks-uri:http://localhost:9101/auth/jwks}")
    private String jwksUri;

    @Value("${jwt.jwks.cache-ttl-seconds:900}")
    private long cacheTtlSeconds;

    private final RestClient httpClient = RestClient.builder().build();

    private volatile Map<String, RSAPublicKey> keyCache = Collections.emptyMap();
    private volatile Instant cacheExpiry = Instant.EPOCH;

    /**
     * Look up an RSA public key by its key ID.
     *
     * @param kid the {@code kid} from the JWT header
     * @return the matching public key, or {@code null} if not found / JWKS disabled
     */
    public RSAPublicKey getKey(String kid) {
        if (!rsaEnabled || kid == null) {
            return null;
        }
        ensureCacheFresh();
        RSAPublicKey key = keyCache.get(kid);
        if (key == null) {
            // Cache miss: force one refresh in case the key rotated
            refreshCache();
            key = keyCache.get(kid);
        }
        return key;
    }

    private synchronized void ensureCacheFresh() {
        if (Instant.now().isAfter(cacheExpiry)) {
            refreshCache();
        }
    }

    private void refreshCache() {
        try {
            String body = httpClient.get()
                    .uri(jwksUri)
                    .retrieve()
                    .body(String.class);
            JsonNode jwks = MAPPER.readTree(body);
            JsonNode keys = jwks.get("keys");
            if (keys == null || !keys.isArray()) {
                log.warn("JWKS response has no 'keys' array: {}", body);
                return;
            }
            Map<String, RSAPublicKey> newCache = new HashMap<>();
            for (JsonNode entry : keys) {
                String k = entry.path("kid").asText();
                String n = entry.path("n").asText();
                String e = entry.path("e").asText();
                if (k.isEmpty() || n.isEmpty() || e.isEmpty()) {
                    continue;
                }
                try {
                    RSAPublicKey rsaKey = buildRsaKey(n, e);
                    newCache.put(k, rsaKey);
                } catch (Exception ex) {
                    log.warn("Failed to parse JWK entry kid={}: {}", k, ex.getMessage());
                }
            }
            this.keyCache = Collections.unmodifiableMap(newCache);
            this.cacheExpiry = Instant.now().plusSeconds(cacheTtlSeconds);
            log.debug("JWKS cache refreshed: {} key(s), expires at {}", newCache.size(), cacheExpiry);
        } catch (Exception ex) {
            log.warn("Failed to refresh JWKS cache from {}: {}", jwksUri, ex.getMessage());
        }
    }

    private RSAPublicKey buildRsaKey(String nB64, String eB64) throws Exception {
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(nB64));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(eB64));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(n, e));
    }
}

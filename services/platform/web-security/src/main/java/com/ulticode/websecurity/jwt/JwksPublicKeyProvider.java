package com.ulticode.websecurity.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/** Bounded, fail-closed RSA JWKS cache shared by all resource owners. */
public final class JwksPublicKeyProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_RSA_MODULUS_BITS = 2048;

    private final boolean enabled;
    private final long cacheTtlSeconds;
    private final long retryBackoffSeconds;
    private final Clock clock;
    private final Supplier<String> source;

    private volatile Map<String, RSAPublicKey> keyCache = Collections.emptyMap();
    private volatile Instant refreshAfter = Instant.EPOCH;

    public JwksPublicKeyProvider(
            boolean enabled,
            String jwksUri,
            String staticJwks,
            long cacheTtlSeconds,
            long retryBackoffSeconds,
            String allowedHosts,
            Environment environment,
            RestClient httpClient) {
        this(enabled, cacheTtlSeconds, retryBackoffSeconds, Clock.systemUTC(),
                source(enabled, jwksUri, staticJwks, allowedHosts, environment, httpClient));
        if (enabled && staticJwks != null && !staticJwks.isBlank()) {
            Map<String, RSAPublicKey> keys = parseKeys(staticJwks);
            if (keys.isEmpty()) {
                throw new IllegalStateException("Static JWKS must contain at least one valid signing key");
            }
            keyCache = keys;
            refreshAfter = Instant.MAX;
        }
    }

    JwksPublicKeyProvider(
            boolean enabled,
            long cacheTtlSeconds,
            long retryBackoffSeconds,
            Clock clock,
            Supplier<String> source) {
        if (cacheTtlSeconds < 30 || cacheTtlSeconds > 3600) {
            throw new IllegalArgumentException("JWKS cache TTL must be between 30 and 3600 seconds");
        }
        if (retryBackoffSeconds < 1 || retryBackoffSeconds > cacheTtlSeconds) {
            throw new IllegalArgumentException("JWKS retry backoff must be between 1 second and the cache TTL");
        }
        this.enabled = enabled;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.retryBackoffSeconds = retryBackoffSeconds;
        this.clock = clock;
        this.source = source;
    }

    public RSAPublicKey getKey(String kid) {
        if (!enabled || kid == null || kid.isBlank()) {
            return null;
        }
        ensureCacheFresh();
        return keyCache.get(kid);
    }

    private synchronized void ensureCacheFresh() {
        Instant now = clock.instant();
        if (now.isBefore(refreshAfter)) {
            return;
        }
        try {
            Map<String, RSAPublicKey> refreshed = parseKeys(source.get());
            if (refreshed.isEmpty()) {
                throw new IllegalStateException("JWKS contains no valid signing keys");
            }
            keyCache = refreshed;
            refreshAfter = now.plusSeconds(cacheTtlSeconds);
        } catch (RuntimeException exception) {
            refreshAfter = now.plusSeconds(retryBackoffSeconds);
        }
    }

    static Map<String, RSAPublicKey> parseKeys(String document) {
        if (document == null || document.isBlank()) {
            throw new IllegalArgumentException("JWKS document must not be blank");
        }
        try {
            JsonNode keys = MAPPER.readTree(document).path("keys");
            if (!keys.isArray()) {
                throw new IllegalArgumentException("JWKS must contain a keys array");
            }
            Map<String, RSAPublicKey> parsed = new HashMap<>();
            for (JsonNode entry : keys) {
                String kid = entry.path("kid").asText("");
                String kty = entry.path("kty").asText("");
                String use = entry.path("use").asText("");
                String alg = entry.path("alg").asText("");
                String modulus = entry.path("n").asText("");
                String exponent = entry.path("e").asText("");
                if (kid.isBlank() || !"RSA".equals(kty)
                        || (!use.isBlank() && !"sig".equals(use))
                        || (!alg.isBlank() && !"RS256".equals(alg))
                        || modulus.isBlank() || exponent.isBlank()) {
                    continue;
                }
                RSAPublicKey key = buildRsaKey(modulus, exponent);
                if (parsed.putIfAbsent(kid, key) != null) {
                    throw new IllegalArgumentException("JWKS contains duplicate kid: " + kid);
                }
            }
            return Collections.unmodifiableMap(parsed);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JWKS document", exception);
        }
    }

    static void validateJwksUri(String jwksUri, String allowedHosts, Environment environment) {
        URI uri = URI.create(jwksUri);
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("JWKS URI is invalid");
        }
        Set<String> hosts = new HashSet<>();
        for (String allowed : allowedHosts.split(",")) {
            if (!allowed.isBlank()) {
                hosts.add(allowed.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!hosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("JWKS host is not allowlisted");
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return;
        }
        boolean localOnly = environment != null && environment.getActiveProfiles().length > 0;
        if (localOnly) {
            for (String profile : environment.getActiveProfiles()) {
                if (!"dev".equals(profile) && !"test".equals(profile) && !"ci".equals(profile)) {
                    localOnly = false;
                    break;
                }
            }
        }
        boolean loopback = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
        if (!"http".equalsIgnoreCase(uri.getScheme()) || !localOnly || !loopback) {
            throw new IllegalStateException("JWKS URI must use HTTPS outside local profiles");
        }
    }

    private static Supplier<String> source(
            boolean enabled,
            String jwksUri,
            String staticJwks,
            String allowedHosts,
            Environment environment,
            RestClient httpClient) {
        if (!enabled) {
            return () -> "{\"keys\":[]}";
        }
        if (staticJwks != null && !staticJwks.isBlank()) {
            return () -> staticJwks;
        }
        validateJwksUri(jwksUri, allowedHosts, environment);
        return () -> httpClient.get().uri(jwksUri).retrieve().body(String.class);
    }

    private static RSAPublicKey buildRsaKey(String modulus, String exponent) throws Exception {
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
        if (n.bitLength() < MIN_RSA_MODULUS_BITS) {
            throw new IllegalArgumentException("RSA modulus must be at least 2048 bits");
        }
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(n, e));
    }
}

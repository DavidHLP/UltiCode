package com.ulticode.common.tracing;

import java.util.UUID;
import java.io.Serializable;

/**
 * Idempotency key carried on mutating RPC calls so a retried producer does
 * not double-apply the side effect.
 *
 * <p>An idempotency token is independent of the trace id: trace ids cluster
 * &quot;together&quot; attempts of the same end-user action; the idempotency
 * key is what the producer dedupes on (typically a UUID v4 minted by the
 * client or the load-balancer).
 *
 * <p>The base record carries the raw token plus optional auxiliary signals
 * (request fingerprint, caller identity); {@link #fingerprint()} is opaque
 * to backend-common &mdash; producers decide the hash strategy.
 *
 * <p>{@code RpcResult.idempotencyKey} mirrors this field on the envelope so
 * the consumer can correlate with what the producer actually honored.
 */
public record IdMetadata(
        String idempotencyKey,
        String fingerprint,
        String issuedBy) implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * Convenience: mint a fresh UUID-v4 idempotency key with no fingerprint
     * or issuer metadata. Use this on the producer when no client-supplied
     * key was available.
     */
    public static IdMetadata mint() {
        return new IdMetadata(UUID.randomUUID().toString(), null, null);
    }

    /**
     * Convenience: wrap a client-supplied idempotency token with a request
     * fingerprint (e.g. hash of the canonicalized command body).
     *
     * @param idempotencyKey client or LB-supplied token (must be non-blank)
     * @param fingerprint    producer-derived fingerprint (may be null)
     */
    public static IdMetadata of(String idempotencyKey, String fingerprint) {
        return new IdMetadata(idempotencyKey, fingerprint, null);
    }

    /**
     * @return true when {@link #idempotencyKey} is a non-blank string.
     */
    public boolean hasKey() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }
}

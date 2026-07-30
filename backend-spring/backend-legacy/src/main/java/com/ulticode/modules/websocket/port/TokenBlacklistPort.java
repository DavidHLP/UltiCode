package com.ulticode.modules.websocket.port;

/**
 * Read-only revocation seam consulted by the WebSocket authentication path
 * before a STOMP CONNECT is accepted.
 *
 * <p>Consumer-owned (the websocket module needs the answer; the adapter decides
 * how to compute it). Mirrors the project's port/adapter discipline already
 * established by {@link com.ulticode.modules.notification.port.NotificationPushPort},
 * {@link com.ulticode.modules.achievement.port.BadgePushPort}, etc.
 *
 * <p><strong>Surface decision &mdash; read only.</strong> The previous
 * {@code com.ulticode.common.service.TokenBlacklistService} fused the read
 * path with three unused write methods ({@code blacklistToken},
 * {@code blacklistToken(ttl)}, {@code removeFromBlacklist}). A repo-wide
 * audit ({@code grep blacklistToken|removeFromBlacklist}) found zero
 * production callers &mdash; runtime token revocation is owned by
 * {@code com.ulticode.auth.refreshtoken.service.RefreshTokenService}
 * (DB-backed hash-only storage; see backend-auth refreshtoken module
 * and V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql). The write
 * methods were speculative dead code; they are not ported. If a future
 * admin instant-revoke feature needs to populate a Redis blacklist, it
 * should define its own writer-owned port
 * (e.g. {@code TokenRevocationWritePort} in the auth module) rather than
 * widening this read-side interface.
 *
 * <p><strong>Deletion test passes.</strong> Removing this port would force
 * {@link com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor}
 * to re-import {@code StringRedisTemplate} + SHA-256 hashing + the Redis
 * key-prefix convention, restart a Redis test container for unit tests,
 * and lose the ability to swap the lookup strategy (e.g. a Caffeine-backed
 * bloom filter for hot-path pre-filtering) without touching the consumer.
 *
 * <p><strong>Contract &mdash; fail-closed.</strong>
 * <ul>
 *   <li>Idempotent, side-effect free, safe to call concurrently.</li>
 *   <li><strong>Throws on storage error.</strong> This is an auth seam: a
 *       Redis outage must NOT silently allow revoked tokens to connect
 *       (fail-open would defeat the purpose of revocation). Implementations
 *       let the underlying {@code RedisConnectionFailureException} (or
 *       equivalent) propagate; {@link
 *       com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor#preSend}
 *       treats the resulting CONNECT as failed. The pre-existing
 *       {@code TokenBlacklistService} had the same behaviour &mdash; this
 *       port preserves it.</li>
 *   <li>Token is the raw JWT string; the adapter is responsible for any
 *       hashing before storage lookup (see
 *       {@link com.ulticode.modules.websocket.port.adapter.RedisTokenBlacklistAdapter}
 *       for the SHA-256 + key-prefix convention).</li>
 * </ul>
 *
 * @author ulticode
 */
public interface TokenBlacklistPort {

    /**
     * Has this JWT been explicitly revoked via the blacklist store?
     *
     * <p>Returning {@code false} here does <em>not</em> authorise the
     * connection &mdash; the interceptor still runs full JWT signature
     * and expiry validation afterwards. This is one input among several.
     *
     * @param token the raw JWT string (never {@code null})
     * @return {@code true} iff the token is recorded as revoked in the
     *         backing store; {@code false} iff the store confirmed the token
     *         is not present
     * @throws org.springframework.dao.DataAccessException if the backing
     *         store is unreachable (fail-closed &mdash; see class contract)
     */
    boolean isBlacklisted(String token);
}

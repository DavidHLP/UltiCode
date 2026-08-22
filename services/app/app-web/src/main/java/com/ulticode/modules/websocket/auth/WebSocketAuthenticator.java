package com.ulticode.modules.websocket.auth;

import com.ulticode.modules.websocket.dto.SocketClientData;

import java.util.Optional;

/**
 * Deep module that owns the WebSocket authentication policy.
 *
 * <p>Before this module existed, {@link
 * com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor}
 * mixed STOMP plumbing with the entire authentication pipeline: token
 * extraction, blacklist check, JWT signature/expiry validation, payload
 * sanity, user existence lookup, and principal construction. Every test
 * stubbed the whole stack.
 *
 * <p>The split:
 * <ul>
 *   <li>{@link com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor}
 *       is a STOMP adapter: it speaks the transport and delegates here.</li>
 *   <li>This module owns the policy: token presence, blacklist, signature,
 *       expiry, payload, and user existence.</li>
 * </ul>
 *
 * <p>Preserved contracts (must not regress):
 * <ol>
 *   <li><b>Fail-closed.</b> TokenBlacklistPort failures must propagate;
 *       the consumer treats the CONNECT as failed rather than silently
 *       admitting a possibly-revoked token.</li>
 *   <li><b>Consumer-owned seam.</b> The blacklist port stays here; the
 *       module does not import {@code StringRedisTemplate} or any storage
 *       detail.</li>
 * </ol>
 *
 * @author ulticode
 */
public interface WebSocketAuthenticator {

    /**
     * Authenticate a STOMP CONNECT. Returns the principal data on success.
     *
     * @param tokenOpt the token candidate copied from the handshake session
     *                 attribute; empty when the cookie was absent
     * @return the populated {@link SocketClientData}
     * @throws com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException
     *         when the policy rejects the connection
     */
    SocketClientData authenticate(Optional<String> tokenOpt);
}

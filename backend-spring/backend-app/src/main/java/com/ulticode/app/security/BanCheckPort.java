package com.ulticode.app.security;

/**
 * Port the ban-check aspect uses to determine whether the current
 * principal is banned from posting content.
 *
 * <p>App-side replacement for legacy
 * {@code com.ulticode.common.audit.BanCheckPort}. The adapter
 * implementation queries {@code IdentityQueryService} via Dubbo RPC
 * instead of reading the legacy {@code UserMapper} directly.
 *
 * <p>P7-RELOCATE-SOLUTION-001: required when backend-app stopped depending
 * on backend-legacy.
 */
public interface BanCheckPort {

    /**
     * @param userId the principal to check
     * @return {@code true} if the user is currently banned from posting
     *         content; {@code false} otherwise (including when the user
     *         does not exist or the RPC fails — ban check is
     *         non-throwing by contract)
     */
    boolean isBanned(String userId);
}

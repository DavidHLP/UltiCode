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
     *         content; {@code false} only after Auth proves the user is not banned
     * @throws com.ulticode.common.exception.BusinessException when Auth cannot
     *         prove the ban state
     */
    boolean isBanned(String userId);
}

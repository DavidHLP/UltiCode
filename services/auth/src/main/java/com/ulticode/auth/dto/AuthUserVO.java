package com.ulticode.auth.dto;

import java.io.Serializable;

/**
 * Frontend-facing user VO returned by {@code /auth/login}, {@code /auth/register},
 * {@code /auth/refresh}, and {@code /auth/me}. Field names match the
 * {@code shared/auth-core/src/types.ts} {@code User} interface contract:
 * {@code id}, {@code username}, {@code name}, {@code email}, {@code role},
 * {@code is_active}, {@code is_banned}, {@code joined_at}.
 *
 * <p>This is intentionally separate from the Dubbo contract
 * {@code UserIdentityDTO} (which uses {@code accountId}/{@code active}/
 * {@code banned} for inter-service RPC). The auth HTTP layer translates
 * from the internal {@code AuthAccountRecord} to this VO so the frontend
 * receives the field names it was designed against.
 */
public record AuthUserVO(
        String id,
        String username,
        String name,
        String email,
        String role,
        boolean is_active,
        boolean is_banned,
        String joined_at) implements Serializable {
    private static final long serialVersionUID = 1L;

}

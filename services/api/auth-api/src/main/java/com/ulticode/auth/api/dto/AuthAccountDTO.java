package com.ulticode.auth.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Pure Auth-owned account projection.
 *
 * <p>Carries authentication, authorization version, and lifecycle fields owned
 * by the {@code backend-auth} module. Public user profile fields (such as
 * {@code name}, {@code avatar}, {@code bio}) belong to {@code user_profiles}
 * owned by {@code backend-app} and are intentionally not present here.
 */
public record AuthAccountDTO(
        String accountId,
        String username,
        String email,
        String role,
        boolean active,
        boolean banned,
        String bannedReason,
        LocalDateTime bannedUntil,
        LocalDateTime joinedAt,
        LocalDateTime lastLoginAt,
        long authzVersion) implements Serializable {
    private static final long serialVersionUID = 1L;

}

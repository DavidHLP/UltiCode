package com.ulticode.auth.account;

import java.time.LocalDateTime;

/**
 * Lightweight account record inside backend-auth.
 */
public record AuthAccountRecord(
        String id,
        String username,
        String email,
        String password,
        String role,
        Boolean isActive,
        Boolean isBanned,
        LocalDateTime bannedUntil,
        LocalDateTime joinedAt
) {}

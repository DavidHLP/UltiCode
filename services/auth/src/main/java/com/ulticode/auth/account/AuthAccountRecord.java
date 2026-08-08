package com.ulticode.auth.account;

import java.time.LocalDateTime;

/** Authoritative authentication-side account projection. */
public record AuthAccountRecord(
        String id,
        String username,
        String email,
        String password,
        String role,
        Boolean isActive,
        Boolean isBanned,
        LocalDateTime bannedUntil,
        LocalDateTime joinedAt,
        long authzVersion
) {
    /** Compatibility constructor for callers that create a new, unversioned account. */
    public AuthAccountRecord(String id, String username, String email, String password,
                             String role, Boolean isActive, Boolean isBanned,
                             LocalDateTime bannedUntil, LocalDateTime joinedAt) {
        this(id, username, email, password, role, isActive, isBanned,
                bannedUntil, joinedAt, 0L);
    }
}

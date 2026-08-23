package com.ulticode.auth.account.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** Persistence projection for Auth-owned columns in {@code users}. */
@Data
public class AuthAccountEntity {
    private String id;
    private String username;
    private String email;
    private String password;
    private String role;
    private Boolean active;
    private Boolean banned;
    private String bannedReason;
    private LocalDateTime bannedUntil;
    private LocalDateTime joinedAt;
    private LocalDateTime lastLoginAt;
    private Long authzVersion;
    private LocalDateTime deletedAt;
    private String passwordResetTokenHash;
    private LocalDateTime passwordResetExpiresAt;
}

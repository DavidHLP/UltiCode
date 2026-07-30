package com.ulticode.modules.auth.account;

import com.ulticode.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for authentication-related account persistence operations.
 * Allows switching between database and mock/in-memory implementations.
 */
public interface AuthAccountPort {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findById(String userId);

    User create(User user);

    void updateLastLoginAt(String userId);

    void updatePassword(String userId, String hashedPassword);

    void updateBanStatus(String userId, boolean isBanned, String bannedReason);

    void updateActiveStatus(String userId, boolean isActive);

    void deleteAccount(String userId);

    void updateAccountCredentials(String userId, String username, String email, String role);

    record PasswordResetRecord(String userId, String tokenHash, long expiresAtEpochMs) {}

    Optional<PasswordResetRecord> findPasswordReset(String userId);

    void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs);

    void clearPasswordReset(String userId);

    Optional<User> findByOAuthEmail(String email);

    List<User> findUsersWithActivePasswordReset(long nowEpochMs);
}

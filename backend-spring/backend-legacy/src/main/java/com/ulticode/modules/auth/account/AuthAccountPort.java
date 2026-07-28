package com.ulticode.modules.auth.account;

import com.ulticode.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Authentication-side account persistence seam (P3-OWNER-002).
 *
 * <p>Consumer-owned port that hides the {@code User} entity (and any
 * underlying mapper / table layout) from the authentication modules.
 */
public interface AuthAccountPort {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findById(String userId);

    User create(User user);

    void updateLastLoginAt(String userId);

    void updatePassword(String userId, String hashedPassword);

    void updateBanStatus(String userId, boolean isBanned, String bannedReason);

    void deleteAccount(String userId);

    void updateAccountCredentials(String userId, String username, String email, String role);

    Optional<PasswordResetRecord> findPasswordReset(String userId);

    void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs);

    void clearPasswordReset(String userId);

    List<User> findUsersWithActivePasswordReset(long nowEpochMs);

    Optional<User> findByOAuthEmail(String email);

    record PasswordResetRecord(String userId, String token, long expiresAtEpochMs) {}
}

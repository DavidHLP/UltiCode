package com.ulticode.auth.account;

import java.util.List;
import java.util.Optional;

/**
 * Authentication-side account persistence seam.
 */
public interface AuthAccountPort {

    Optional<AuthAccountRecord> findByUsername(String username);

    Optional<AuthAccountRecord> findByEmail(String email);

    Optional<AuthAccountRecord> findById(String userId);

    AuthAccountRecord create(AuthAccountRecord record);

    void updateLastLoginAt(String userId);

    void updatePassword(String userId, String hashedPassword);

    Optional<PasswordResetRecord> findPasswordReset(String userId);

    void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs);

    void clearPasswordReset(String userId);

    List<AuthAccountRecord> findUsersWithActivePasswordReset(long nowEpochMs);

    record PasswordResetRecord(String userId, String token, long expiresAtEpochMs) {}
}

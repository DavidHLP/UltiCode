package com.ulticode.auth.account;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Authentication-side authoritative account persistence seam. */
public interface AuthAccountPort {
    Optional<AuthAccountRecord> findByUsername(String username);
    Optional<AuthAccountRecord> findByEmail(String email);
    Optional<AuthAccountRecord> findById(String userId);
    List<AuthAccountRecord> findByIds(Set<String> userIds);
    /** Returns active, non-banned, non-deleted account ids for internal fan-out. */
    List<String> findActiveAccountIds();
    AuthAccountRecord create(AuthAccountRecord record);
    void updateLastLoginAt(String userId);
    void updatePassword(String userId, String hashedPassword);
    /** Unified atomic CAS update for active, banned, and role. Bumps authzVersion once. */
    boolean updateAccountIfVersion(String userId, boolean active, boolean banned, String role, long expectedVersion);
    /** Atomically increments authzVersion only when expectedVersion matches. */
    boolean bumpAuthzVersionIfExpected(String userId, long expectedVersion);
    Optional<PasswordResetRecord> findPasswordReset(String userId);
    void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs);
    void clearPasswordReset(String userId);
    List<AuthAccountRecord> findUsersWithActivePasswordReset(long nowEpochMs);
    record PasswordResetRecord(String userId, String token, long expiresAtEpochMs) {}
}

package com.ulticode.modules.auth.account;

import com.ulticode.modules.user.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Authentication-side account persistence seam.
 *
 * <p>Consumer-owned port that hides the {@code User} entity (and any
 * underlying mapper / table layout) from the authentication modules.
 * Login, register, OAuth, and password reset all depend on this contract
 * instead of {@code UserMapper} directly, so the auth side stays decoupled
 * from user-table column changes.
 *
 * <p>The implementation lives in the {@code user} module (see
 * {@link com.ulticode.modules.auth.account.DefaultAuthAccountAdapter}),
 * preserving the existing dependency direction (auth → user) without
 * leaking user-table specifics into auth. Architecture-review candidate #5.
 *
 * @author ulticode
 */
public interface AuthAccountPort {

    /**
     * Look up a user by username. Used by login and registration
     * uniqueness checks.
     */
    Optional<User> findByUsername(String username);

    /**
     * Look up a user by primary email. Used by password reset and
     * register.
     */
    Optional<User> findByEmail(String email);

    /**
     * Look up a user by primary key.
     */
    Optional<User> findById(String userId);

    /**
     * Persist a brand-new user record (called from register). Returns
     * the entity after insertion (including any DB-generated fields).
     */
    User create(User user);

    /**
     * Update the user's password hash. Used by password reset completion
     * and any future credential rotation.
     */
    void updatePassword(String userId, String hashedPassword);

    /**
     * Look up the reset-token record for a user. The token record lives
     * alongside the user in the user module, so the auth service does not
     * touch it directly.
     */
    Optional<PasswordResetRecord> findPasswordReset(String userId);

    /**
     * Persist a new password-reset token (call site issues the token, the
     * adapter decides storage layout).
     */
    void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs);

    /**
     * Invalidate any active password-reset tokens for a user (call after a
     * successful reset).
     */
    void clearPasswordReset(String userId);

    /**
     * List every user that currently has a non-null, non-expired reset
     * token. Used by {@code PasswordResetService.resetPassword} to find the
     * candidate before BCrypt-matching the supplied token.
     */
    List<User> findUsersWithActivePasswordReset(long nowEpochMs);

    /**
     * Look up the user matched by OAuth provider user info. The current
     * implementation matches on email (the only field shared across the
     * identity providers we support). When a real {@code user_oauth_identities}
     * table is introduced, the adapter can switch internally without
     * changing the auth-side contract.
     */
    Optional<User> findByOAuthEmail(String email);

    /**
     * Update the user's avatar when the OAuth provider reports a new one.
     */
    void updateAvatar(String userId, String avatarUrl);

    /**
     * Minimal record exposed to the auth side. The full
     * {@code password_reset_tokens} table is owned by the user module; the
     * auth side only needs the token, the owning userId, and the
     * expiration.
     */
    record PasswordResetRecord(String userId, String token, long expiresAtEpochMs) {}
}

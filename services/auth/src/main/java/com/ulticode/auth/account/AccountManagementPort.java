package com.ulticode.auth.account;

import java.util.Optional;

/**
 * Narrow auth-owned write port for administrative account management.
 *
 * <p>This port deliberately stays separate from {@link AuthAccountPort}: the
 * existing authentication port is also consumed by legacy adapters, while
 * this seam carries account-governance mutations and soft-delete metadata.
 */
public interface AccountManagementPort {

    Optional<AuthAccountRecord> findById(String accountId);

    Optional<AuthAccountRecord> findByUsername(String username);

    Optional<AuthAccountRecord> findByEmail(String email);

    AuthAccountRecord create(AuthAccountRecord account);

    boolean updateCredentials(String accountId, String username, String email,
                              String updatedBy);

    boolean updatePassword(String accountId, String hashedPassword, String updatedBy);

    boolean softDelete(String accountId, String deletedBy);
}

package com.ulticode.auth.permission.port;

import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;

/**
 * Port for changing the {@code users.role} column from backend-auth.
 *
 * <p>P2-RBAC-001: this is the only seam through which
 * {@code users.role} may be mutated while Phase 2 / Phase 3 are in
 * flight. App / Admin / legacy modules must call the HTTP command
 * surface (see
 * {@link com.ulticode.auth.adapter.in.web.RoleAdministrationController})
 * rather than writing to the table directly. The ArchUnit
 * foreign-writer rule enforces this at compile time.
 *
 * <p>Implementations are expected to be idempotent against the same
 * role value (no-op when current == new) and to bump a per-user
 * change counter that downstream consumers (cache invalidation,
 * Phase 6 outbox event) can subscribe to.
 */
public interface UserRoleWritePort {

    /**
     * Persist the new role for the given user id.
     *
     * @param userId target user id (non-null, non-blank)
     * @param newRole new role value; must be one of USER / MODERATOR /
     *     ADMIN / SUPER_ADMIN (case-insensitive, uppercased before write)
     * @return the new authoritative role value (uppercase), useful
     *     for confirming the write took effect
     * @throws AuthBusinessException with {@link AuthErrorCode#AUTH_USER_NOT_FOUND}
     *     if the user id does not exist
     */
    String changeRole(String userId, String newRole);
}

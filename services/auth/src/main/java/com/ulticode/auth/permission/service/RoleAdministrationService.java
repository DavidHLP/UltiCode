package com.ulticode.auth.permission.service;

import java.time.LocalDateTime;

/**
 * P2-RBAC-001 owner-only write surface for the Auth-owned
 * {@code users.role} / {@code user_permissions} tables.
 *
 * <p>The HTTP compatibility adapter and the Dubbo administration workflow
 * both terminate at Auth-owned services; neither caller receives persistence
 * entities or mapper types.</p>
 */

public interface RoleAdministrationService {

    /**
     * Change a user's role. Idempotent: writing the same role the
     * user already has is a no-op (still emits a "no-op" event for
     * observability).
     *
     * @return the authoritative role value (uppercase) after the write
     */
    String changeRole(String userId, String newRole, String actorId);

    /**
     * Grant a direct permission. The returned value is a transport-safe
     * projection rather than the persistence entity.
     */
    PermissionGrant grantPermission(String userId, String action, String resource,
                                    LocalDateTime expiresAt, String actorId);

    /**
     * Revoke a direct permission.
     */
    boolean revokePermission(String userId, String action, String resource, String actorId);

    /** Transport-safe value object for the HTTP compatibility response. */
    record PermissionGrant(
            String id,
            String userId,
            String resource,
            String action,
            String grantedBy,
            LocalDateTime grantedAt,
            LocalDateTime expiresAt) {
    }
}

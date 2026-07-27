package com.ulticode.auth.permission.service;

import com.ulticode.auth.permission.entity.UserPermission;

import java.time.LocalDateTime;

/**
 * P2-RBAC-001 owner-only write surface for the
 * {@code users.role} / {@code user_permissions} / (future)
 * {@code role_permissions} tables. The implementation in
 * {@code backend-auth} is the single writer; App / Admin / legacy
 * modules must call this service via the HTTP command surface
 * (see {@code RoleAdministrationController}) rather than touching
 * the underlying mappers directly. The ArchUnit foreign-writer
 * rule enforces this boundary.
 *
 * <p>Events ({@code RoleChanged}, {@code PermissionChanged}) are
 * emitted as structured log lines from this layer; the durable
 * outbox wiring is owned by Phase 6 (P6-OUTBOX-001).
 */
public interface RoleAdministrationService {

    /**
     * Change a user's role. Idempotent: writing the same role the
     * user already has is a no-op (still emits a "no-op" event for
     * observability).
     *
     * @return the authoritative role value (uppercase) after the
     *     write
     */
    String changeRole(String userId, String newRole, String actorId);

    /**
     * Grant a direct permission. Delegates to the existing
     * {@link PermissionService#assignPermission(String, String, String, LocalDateTime)}
     * so idempotency and expiry semantics stay consistent.
     */
    UserPermission grantPermission(String userId, String action, String resource,
                                   LocalDateTime expiresAt, String actorId);

    /**
     * Revoke a direct permission. Delegates to the existing
     * {@link PermissionService#revokePermission(String, String, String)}.
     */
    boolean revokePermission(String userId, String action, String resource, String actorId);
}

package com.ulticode.auth.permission.service.impl;

import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.UserRoleMapper;
import com.ulticode.auth.permission.port.UserRoleWritePort;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.auth.permission.service.RoleAdministrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * P2-RBAC-001 implementation and sole owner of the Auth-owned
 * {@code users.role} / {@code user_permissions} write path.
 *
 * <p>Callers receive value objects or scalar results; persistence entities and
 * mappers remain behind this service boundary.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdministrationServiceImpl implements RoleAdministrationService {

    /** Roles allowed as targets of the change-role command. */
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "MODERATOR", "ADMIN", "SUPER_ADMIN");

    private final UserRoleWritePort userRoleWritePort;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;

    @Override
    @Transactional
    public String changeRole(String userId, String newRole, String actorId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (newRole == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_REQUEST);
        }
        final String role = newRole.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(role)) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_REQUEST,
                    "Unsupported role: " + newRole);
        }
        // Existence check translates a 0-row UPDATE into a 404 rather than a silent no-op.
        if (userRoleMapper.existsById(userId) == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        final String applied = userRoleWritePort.changeRole(userId, role);
        // P6-OUTBOX-001 will replace this structured log with a durable outbox event.
        log.info("RBAC event=RoleChanged subject={} newRole={} actor={} ts={}",
                userId, applied, actorId, LocalDateTime.now());
        return applied;
    }

    @Override
    @Transactional
    public PermissionGrant grantPermission(String userId, String action, String resource,
                                           LocalDateTime expiresAt, String actorId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (userRoleMapper.existsById(userId) == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        final UserPermission granted = permissionService.assignPermission(userId, action, resource, expiresAt);
        log.info("RBAC event=PermissionChanged action=GRANT subject={} action={} resource={} actor={} ts={}",
                userId, action, resource, actorId, LocalDateTime.now());
        return new PermissionGrant(
                granted.getId(),
                granted.getUserId(),
                granted.getResource(),
                granted.getAction(),
                granted.getGrantedBy(),
                granted.getGrantedAt(),
                granted.getExpiresAt());
    }

    @Override
    @Transactional
    public boolean revokePermission(String userId, String action, String resource, String actorId) {
        if (userId == null || userId.isBlank()) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (userRoleMapper.existsById(userId) == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        final boolean removed = permissionService.revokePermission(userId, action, resource);
        log.info("RBAC event=PermissionChanged action=REVOKE subject={} action={} resource={} removed={} actor={} ts={}",
                userId, action, resource, removed, actorId, LocalDateTime.now());
        return removed;
    }
}

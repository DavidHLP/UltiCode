package com.ulticode.auth.permission.service.impl;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountMapper;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.UserRoleMapper;
import com.ulticode.auth.permission.port.UserRoleWritePort;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.auth.permission.service.RoleAdministrationService;
import com.ulticode.common.audit.AuditSinkPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final AuthAccountMapper authAccountMapper;
    private final AuditSinkPort auditSinkPort;

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
        AuthAccountEntity before = requireAccount(userId);
        final String applied = userRoleWritePort.changeRole(userId, role);
        AuthAccountEntity after = requireAccount(userId);
        if (authorizationVersion(after) != authorizationVersion(before)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("change", "ROLE");
            change.put("role", applied);
            emitAuthorizationChange(userId, actorId, change, after);
        }
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
        if (authAccountMapper.bumpAuthzVersion(userId) != 1) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("change", "PERMISSION_GRANTED");
        change.put("action", action);
        change.put("resource", resource);
        change.put("permissionId", granted.getId());
        emitAuthorizationChange(userId, actorId, change, requireAccount(userId));
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
        if (removed) {
            if (authAccountMapper.bumpAuthzVersion(userId) != 1) {
                throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("change", "PERMISSION_REVOKED");
            change.put("action", action);
            change.put("resource", resource);
            emitAuthorizationChange(userId, actorId, change, requireAccount(userId));
        }
        return removed;
    }

    private AuthAccountEntity requireAccount(String userId) {
        AuthAccountEntity account = authAccountMapper.findById(userId);
        if (account == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        return account;
    }

    private void emitAuthorizationChange(String userId, String actorId,
                                          Map<String, Object> change,
                                          AuthAccountEntity account) {
        Map<String, Object> payload = new LinkedHashMap<>(change);
        payload.put("authzVersion", authorizationVersion(account));
        String performerId = actorId == null || actorId.isBlank() ? "system" : actorId;
        auditSinkPort.log(
                performerId,
                userId,
                "AUTHORIZATION_CHANGED",
                "USER_AUTHORIZATION",
                userId,
                null,
                payload,
                "unknown",
                null);
        log.info("Durable RBAC authorization event recorded: subject={}, actor={}, version={}",
                userId, performerId, authorizationVersion(account));
    }

    private static long authorizationVersion(AuthAccountEntity account) {
        return account.getAuthzVersion() == null ? 0L : account.getAuthzVersion();
    }
}

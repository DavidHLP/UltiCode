package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.modules.auth.service.AuthCutoverService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

    private final UserMapper userMapper;
    private final BackendAuthRoleAdminClient backendAuthRoleAdminClient;
    private final AuthCutoverService authCutoverService;
    private final AdminUserProjection adminUserProjection;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.GRANT_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AdminUserVO assignUserPermission(String id, String action, String resource,
                                             LocalDateTime expiresAt) {
        requireSuperAdminForManagePermissionsSystem(action, resource);

        return performPermissionChange(id, action, resource, expiresAt, false);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.REVOKE_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AdminUserVO revokeUserPermission(String id, String action, String resource) {
        requireSuperAdminForManagePermissionsSystem(action, resource);

        return performPermissionChange(id, action, resource, null, true);
    }

    private AdminUserVO performPermissionChange(String id, String action, String resource,
                                                 LocalDateTime expiresAt, boolean isRevoke) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }

        final AdminUserVO beforeVo = adminUserProjection.getUserById(id);
        final boolean alreadyPresent = beforeVo != null
                && beforeVo.getPermissions() != null
                && beforeVo.getPermissions().stream().anyMatch(p ->
                        action.equals(p.getAction()) && resource.equals(p.getResource()));

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("action", action);
        oldValues.put("resource", resource);
        oldValues.put("alreadyPresent", alreadyPresent);
        AuditContext.setOldValues(oldValues);

        if (authCutoverService != null) {
            String actorId = currentUserProvider != null ? currentUserProvider.getCurrentUserId() : "admin";
            ActorDelegation actor = new ActorDelegation("ADMIN", actorId, actorId, isRevoke ? "revoke perm" : "grant perm");

            // Compute target full replacement permission set
            Set<String> targetPermissions = new HashSet<>();
            if (beforeVo != null && beforeVo.getPermissions() != null) {
                targetPermissions = beforeVo.getPermissions().stream()
                        .map(p -> p.getAction() + ":" + p.getResource())
                        .collect(Collectors.toSet());
            }

            String targetPermStr = action + ":" + resource;
            if (isRevoke) {
                targetPermissions.remove(targetPermStr);
            } else {
                targetPermissions.add(targetPermStr);
            }

            // Bind idempotency key to current request/trace identity so retries share key but distinct operations over time do not collide
            String traceId = TraceIdUtil.current();
            if (traceId == null || traceId.isBlank()) {
                traceId = "t-" + UUID.randomUUID().toString();
            }
            String stableKey = "auth-perm-" + traceId + "-" + id + "-" + action + "-" + resource;
            String commandId = UUID.nameUUIDFromBytes(stableKey.getBytes()).toString();

            ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                    commandId, IdMetadata.of(stableKey, null), actor, new TraceMetadata(traceId, null, null, null),
                    id, 0L, user.getRole(), targetPermissions, isRevoke ? "revoke permission" : "grant permission"
            );
            authCutoverService.changeAuthorization(command);
        } else if (isRevoke) {
            backendAuthRoleAdminClient.revokePermission(id, action, resource);
        } else {
            final String expiresAtIso = expiresAt == null ? null
                    : expiresAt.atZone(java.time.ZoneId.systemDefault())
                              .toOffsetDateTime()
                              .toString();
            backendAuthRoleAdminClient.grantPermission(id, action, resource, expiresAtIso);
        }

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("action", action);
        newValues.put("resource", resource);
        if (isRevoke) {
            newValues.put("removed", alreadyPresent);
        } else {
            newValues.put("expiresAt", expiresAt != null ? expiresAt : "");
            newValues.put("grantedAt", LocalDateTime.now(clock));
        }
        AuditContext.setNewValues(newValues);

        if (isRevoke && !alreadyPresent) {
            log.info("Revoke no-op (permission not present): user={} {}:{}",
                id, action, resource);
        } else if (!isRevoke) {
            log.info("Permission assigned via cutover/backend-auth: user={} {}:{} expiresAt={}",
                id, action, resource, expiresAt);
        }
        return adminUserProjection.getUserById(id);
    }

    private void requireSuperAdminForManagePermissionsSystem(String action, String resource) {
        if (!"MANAGE_PERMISSIONS".equalsIgnoreCase(action) || !"SYSTEM".equalsIgnoreCase(resource)) {
            return;
        }
        if (!currentUserProvider.hasRole("SUPER_ADMIN")) {
            throw new BusinessException(AdminErrorCode.FORBIDDEN,
                "Granting/revoking MANAGE_PERMISSIONS:SYSTEM requires SUPER_ADMIN role");
        }
    }
}

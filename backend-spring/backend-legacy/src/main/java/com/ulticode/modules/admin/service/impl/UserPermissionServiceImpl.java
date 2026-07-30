package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.modules.auth.service.AuthCutoverService;
import com.ulticode.modules.permission.PermissionVocabulary;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

    private final UserMapper userMapper;
    private final BackendAuthRoleAdminClient backendAuthRoleAdminClient;
    private final AuthCutoverService authCutoverService;
    private final AdminUserProjection adminUserProjection;
    private final Clock clock;
    private final PermissionVocabulary vocabulary;
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
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
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
            ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                    id, 0L, user.getRole(), java.util.Set.of(action + ":" + resource), "permission change"
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
        if (!vocabulary.isSuperAdminOnly(action, resource)) {
            return;
        }
        if (!currentUserProvider.hasRole("SUPER_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                "Granting/revoking MANAGE_PERMISSIONS:SYSTEM requires SUPER_ADMIN role");
        }
    }
}

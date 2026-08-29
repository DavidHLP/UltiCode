package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.ulticode.common.rpc.RpcPolicy;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

    private final AdminUserEnricher userEnricher;
    private final AdminUserProjection adminUserProjection;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountAdministrationService accountAdministrationService;

    @Override
    @Audited(action = AuditVocabulary.GRANT_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AdminUserVO assignUserPermission(String id, String action, String resource,
                                             LocalDateTime expiresAt) {
        requireSuperAdminForManagePermissionsSystem(action, resource);

        return performPermissionChange(id, action, resource, expiresAt, false);
    }

    @Override
    @Audited(action = AuditVocabulary.REVOKE_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AdminUserVO revokeUserPermission(String id, String action, String resource) {
        requireSuperAdminForManagePermissionsSystem(action, resource);

        return performPermissionChange(id, action, resource, null, true);
    }

    private AdminUserVO performPermissionChange(String id, String action, String resource,
                                                 LocalDateTime expiresAt, boolean isRevoke) {
        AdminUserSummary user = userEnricher.enrichOne(id);
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

        if (accountAdministrationService == null) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "AccountAdministrationService unavailable");
        }
        String actorId = currentUserProvider == null ? null : currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        ActorDelegation actor = new ActorDelegation(
                currentUserProvider.hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : "ADMIN",
                actorId, actorId, isRevoke ? "revoke perm" : "grant perm");

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
                id, 0L, user.role(), targetPermissions, isRevoke ? "revoke permission" : "grant permission"
        );
        RpcResult<?> result = accountAdministrationService.changeAuthorization(command);
        if (result == null || !result.success()) {
            log.warn("AccountAdministrationService.changeAuthorization failed for user {}: {}", id,
                    result == null ? "null response" : result.error());
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "Permission change failed on Auth provider");
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
            log.info("Permission assigned via backend-auth RPC: user={} {}:{} expiresAt={}",
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

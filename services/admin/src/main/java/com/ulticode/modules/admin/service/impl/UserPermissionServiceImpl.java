package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.query.AdminUserDetailQuery;
import com.ulticode.modules.admin.query.AdminUserDetailResult;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UserPermissionServiceImpl implements UserPermissionService {

    private final AdminUserDetailQuery adminUserDetailQuery;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;
    @Autowired
    public UserPermissionServiceImpl(
            AdminUserDetailQuery adminUserDetailQuery,
            Clock clock,
            CurrentUserProvider currentUserProvider) {
        this.adminUserDetailQuery = Objects.requireNonNull(
                adminUserDetailQuery, "adminUserDetailQuery");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.currentUserProvider = Objects.requireNonNull(
                currentUserProvider, "currentUserProvider");
    }

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
        AdminUserDetailResult before;
        try {
            before = adminUserDetailQuery.loadUserDetail(id);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw permissionSnapshotUnavailable(
                    action, resource, "detail query failed", exception);
        }
        if (before == null) {
            throw permissionSnapshotUnavailable(
                    action, resource, "detail query returned null", null);
        }
        if (before.failure() == AdminUserDetailResult.Failure.NOT_FOUND) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        if (before.failure() == AdminUserDetailResult.Failure.TRANSPORT_UNAVAILABLE) {
            throw permissionSnapshotUnavailable(
                    action, resource, "detail query unavailable", null);
        }

        AdminUserDetailResult.Section permissionSection = before.permissions();
        AdminUserDetailResult.PermissionSnapshot permissionSnapshot =
                before.permissionSnapshot();
        if (permissionSection == null
                || permissionSection.status() != AdminUserDetailResult.Availability.OK
                || permissionSnapshot == null
                || permissionSnapshot.permissions() == null) {
            String status = permissionSection == null
                    ? AdminUserDetailResult.Availability.UNAVAILABLE.name()
                    : permissionSection.status().name();
            String reason = permissionSection == null || permissionSection.reason() == null
                    ? "authorization snapshot is incomplete"
                    : permissionSection.reason();
            throw permissionSnapshotUnavailable(action, resource, status + ": " + reason, null);
        }

        String targetPermStr = action + ":" + resource;
        Set<String> currentPermissions = new HashSet<>(permissionSnapshot.permissions());
        boolean alreadyPresent = currentPermissions.contains(targetPermStr);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("action", action);
        oldValues.put("resource", resource);
        oldValues.put("alreadyPresent", alreadyPresent);
        oldValues.put("permissionSnapshotStatus", permissionSection.status().name());
        AuditContext.setOldValues(oldValues);

        if (isRevoke && !alreadyPresent) {
            AuditContext.setNewValues(Map.of("removed", false));
            log.info("Revoke no-op (permission not present): user={} {}:{}",
                    id, action, resource);
            return before.user();
        }

        if (accountAdministrationService == null) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "AccountAdministrationService unavailable");
        }
        String actorId = currentUserProvider == null
                ? null : currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED,
                    "Authenticated admin actor is required");
        }
        ActorDelegation actor = new ActorDelegation(
                AdminActors.typeOf(currentUserProvider),
                actorId, actorId, isRevoke ? "revoke perm" : "grant perm");

        if (isRevoke) {
            currentPermissions.remove(targetPermStr);
        } else {
            currentPermissions.add(targetPermStr);
        }

        String traceId = TraceIdUtil.current();
        if (traceId == null || traceId.isBlank()) {
            traceId = "t-" + UUID.randomUUID();
        }
        String stableKey = "auth-perm-" + traceId + "-" + id + "-" + action + "-" + resource;
        String commandId = UUID.nameUUIDFromBytes(
                stableKey.getBytes(StandardCharsets.UTF_8)).toString();

        ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                commandId,
                IdMetadata.of(stableKey, null),
                actor,
                new TraceMetadata(traceId, null, null, null),
                id,
                permissionSnapshot.version(),
                permissionSnapshot.role(),
                currentPermissions,
                isRevoke ? "revoke permission" : "grant permission");
        RpcResult<?> result = accountAdministrationService.changeAuthorization(command);
        if (result == null || !result.success()) {
            log.warn("AccountAdministrationService.changeAuthorization failed for user {}: {}",
                    id, result == null ? "null response" : result.error());
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

        if (!isRevoke) {
            log.info("Permission assigned via backend-auth RPC: user={} {}:{} expiresAt={}",
                    id, action, resource, expiresAt);
        }
        return userFromDetail(id);
    }

    private AdminUserVO userFromDetail(String id) {
        AdminUserDetailResult after;
        try {
            after = adminUserDetailQuery.loadUserDetail(id);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Admin user detail query unavailable",
                    exception);
        }
        if (after == null || after.failure() == AdminUserDetailResult.Failure.NOT_FOUND) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        if (after.failure() == AdminUserDetailResult.Failure.TRANSPORT_UNAVAILABLE
                || after.user() == null) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Admin user detail query unavailable");
        }
        return after.user();
    }

    private BusinessException permissionSnapshotUnavailable(
            String action, String resource, String reason, Throwable cause) {
        Map<String, Object> failureValues = new HashMap<>();
        failureValues.put("action", action);
        failureValues.put("resource", resource);
        failureValues.put("permissionSnapshotStatus", "UNAVAILABLE");
        failureValues.put("permissionSnapshotReason", reason);
        AuditContext.setOldValues(failureValues);
        String message = "Authorization snapshot unavailable: " + reason;
        return cause == null
                ? new BusinessException(AdminErrorCode.OWNER_QUERY_UNAVAILABLE, message)
                : new BusinessException(
                        AdminErrorCode.OWNER_QUERY_UNAVAILABLE, message, cause);
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

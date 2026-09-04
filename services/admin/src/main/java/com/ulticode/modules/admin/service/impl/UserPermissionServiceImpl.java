package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.AuthorizationMutationService;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.service.UserPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Admin adapter for Auth-owned direct permission deltas. */
@Slf4j
@Service
public class UserPermissionServiceImpl implements UserPermissionService {

    private final CurrentUserProvider currentUserProvider;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AuthorizationMutationService authorizationMutationService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Autowired
    public UserPermissionServiceImpl(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = Objects.requireNonNull(
                currentUserProvider, "currentUserProvider");
    }

    @Override
    @Audited(action = AuditVocabulary.GRANT_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AuthorizationMutationDTO assignUserPermission(String id, String action, String resource,
                                                          LocalDateTime expiresAt) {
        requireSuperAdminForManagePermissionsSystem(action, resource);
        return performPermissionChange(
                id, action, resource, expiresAt, PermissionMutationCommand.Operation.GRANT);
    }

    @Override
    @Audited(action = AuditVocabulary.REVOKE_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AuthorizationMutationDTO revokeUserPermission(String id, String action, String resource) {
        requireSuperAdminForManagePermissionsSystem(action, resource);
        return performPermissionChange(
                id, action, resource, null, PermissionMutationCommand.Operation.REVOKE);
    }

    private AuthorizationMutationDTO performPermissionChange(
            String id, String action, String resource, LocalDateTime expiresAt,
            PermissionMutationCommand.Operation operation) {
        requireText(id, "user id");
        requireText(action, "action");
        requireText(resource, "resource");
        if (authorizationMutationService == null || accountQueryService == null) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Auth authorization mutation is unavailable");
        }
        AuthAccountDTO account = requireAccount(id);
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(
                    AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        String traceId = currentTraceId();
        String stableKey = "auth-perm-" + traceId + "-" + id + "-"
                + operation + "-" + action + "-" + resource;
        String commandId = UUID.nameUUIDFromBytes(
                stableKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        PermissionMutationCommand command = new PermissionMutationCommand(
                commandId,
                IdMetadata.of(stableKey, null),
                new ActorDelegation(
                        AdminActors.typeOf(currentUserProvider), actorId, actorId,
                        operation.name().toLowerCase() + " permission"),
                new TraceMetadata(traceId, null, null, null),
                id, operation, action, resource,
                expiresAt == null ? null : expiresAt.atOffset(java.time.ZoneOffset.UTC),
                account.authzVersion(), operation.name().toLowerCase() + " permission");

        RpcResult<AuthorizationMutationDTO> result =
                authorizationMutationService.mutatePermission(command);
        if (result == null || !result.success() || result.data() == null) {
            throw mapFailure(result);
        }
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("action", action);
        newValues.put("resource", resource);
        newValues.put("operation", operation.name());
        newValues.put("changed", result.data().changed());
        if (expiresAt != null) {
            newValues.put("expiresAt", expiresAt.toString());
        }
        AuditContext.setNewValues(newValues);
        log.info("Permission mutation sent to Auth: user={} operation={} {}:{}",
                id, operation, action, resource);
        return result.data();
    }

    private AuthAccountDTO requireAccount(String id) {
        RpcResult<AuthAccountDTO> result;
        try {
            result = accountQueryService.getAccountById(id);
        } catch (RpcException exception) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Auth account query unavailable", exception);
        }
        if (result != null && result.success() && result.data() != null) {
            return result.data();
        }
        if (result != null && result.error() != null
                && result.error().code() == com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        throw new BusinessException(
                AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                "Auth account query unavailable");
    }

    private static BusinessException mapFailure(RpcResult<?> result) {
        if (result != null && result.error() != null) {
            int code = result.error().code();
            if (code == com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
                return new BusinessException(AdminErrorCode.USER_NOT_FOUND);
            }
            if (code == com.ulticode.auth.api.error.AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT.code()
                    || code == com.ulticode.auth.api.error.AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()
                    || code == com.ulticode.common.error.BaseErrorCode.CONFLICT.code()) {
                return new BusinessException(AdminErrorCode.CONFLICT, "Authorization mutation conflict");
            }
            if (code == com.ulticode.auth.api.error.AuthErrorCode.INVALID_ACCOUNT_REQUEST.code()
                    || code == com.ulticode.common.error.BaseErrorCode.VALIDATION_FAILED.code()) {
                return new BusinessException(AdminErrorCode.VALIDATION_FAILED,
                        "Invalid authorization mutation");
            }
            if (code == com.ulticode.common.error.BaseErrorCode.FORBIDDEN.code()
                    || code == com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_BANNED.code()) {
                return new BusinessException(AdminErrorCode.FORBIDDEN);
            }
        }
        return new BusinessException(
                AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                "Auth authorization mutation unavailable");
    }


    private void requireSuperAdminForManagePermissionsSystem(String action, String resource) {
        String normalizedAction = action == null ? null : action.trim();
        String normalizedResource = resource == null ? null : resource.trim();
        if (!"MANAGE_PERMISSIONS".equalsIgnoreCase(normalizedAction)
                || !"SYSTEM".equalsIgnoreCase(normalizedResource)) {
            return;
        }
        if (!currentUserProvider.hasRole("SUPER_ADMIN")) {
            throw new BusinessException(
                    AdminErrorCode.FORBIDDEN,
                    "Granting/revoking MANAGE_PERMISSIONS:SYSTEM requires SUPER_ADMIN role");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, field + " is required");
        }
    }

    private static String currentTraceId() {
        String traceId = TraceIdUtil.current();
        return traceId == null || traceId.isBlank()
                ? "t-" + UUID.randomUUID() : traceId;
    }
}

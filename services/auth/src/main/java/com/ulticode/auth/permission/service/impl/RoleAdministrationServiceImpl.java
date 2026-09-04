package com.ulticode.auth.permission.service.impl;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.authorization.AuthorizationMutationWorkflow;
import com.ulticode.auth.authorization.RoleMutationWorkflow;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.auth.permission.service.RoleAdministrationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HTTP compatibility Adapter for the Auth-owned authorization mutation
 * Modules. All durable role and permission writes terminate in the same
 * receipt-backed workflows used by the cross-process Providers.
 */
@Service
@RequiredArgsConstructor
public class RoleAdministrationServiceImpl implements RoleAdministrationService {

    private final AuthAccountPort authAccountPort;
    private final PermissionService permissionService;
    private final AuthorizationMutationWorkflow authorizationMutationWorkflow;
    private final RoleMutationWorkflow roleMutationWorkflow;
    private final CommandReceiptExecutor receiptExecutor;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public String changeRole(String userId, String newRole, String actorId) {
        AuthAccountRecord account = requireAccount(userId);
        TraceMetadata trace = currentTrace();
        String stableKey = stableKey("auth-http-role", trace.traceId(), account.id(), newRole);
        ChangeRoleCommand command = new ChangeRoleCommand(
                commandId(stableKey),
                IdMetadata.of(stableKey, null),
                actor(actorId, "change role"),
                trace,
                account.id(),
                requireText(newRole, "role"),
                account.authzVersion(),
                "HTTP role administration");
        RpcResult<AccountMutationDTO> result = receiptExecutor.execute(
                "RoleMutationService", "changeRole", command, AccountMutationDTO.class,
                ignored -> roleMutationWorkflow.changeRole(command));
        return requireSuccess(result).role();
    }

    @Override
    public PermissionGrant grantPermission(String userId, String action, String resource,
                                           LocalDateTime expiresAt, String actorId) {
        AuthAccountRecord account = requireAccount(userId);
        TraceMetadata trace = currentTrace();
        String stableKey = stableKey("auth-http-permission", trace.traceId(), "GRANT",
                account.id(), action, resource, expiresAt == null ? null : expiresAt.toString());
        PermissionMutationCommand command = new PermissionMutationCommand(
                commandId(stableKey),
                IdMetadata.of(stableKey, null),
                actor(actorId, "grant permission"),
                trace,
                account.id(),
                PermissionMutationCommand.Operation.GRANT,
                requireText(action, "action"),
                requireText(resource, "resource"),
                expiresAt == null ? null : expiresAt.atOffset(ZoneOffset.UTC),
                account.authzVersion(),
                "HTTP permission administration");
        RpcResult<AuthorizationMutationDTO> result = receiptExecutor.execute(
                "AuthorizationMutationService", "mutatePermission", command,
                AuthorizationMutationDTO.class,
                ignored -> authorizationMutationWorkflow.mutatePermission(command));
        requireSuccess(result);
        UserPermission granted = findDirectPermission(account.id(), command.action(), command.resource());
        if (granted == null) {
            throw new AuthBusinessException(
                    AuthErrorCode.UNEXPECTED_AUTH_STATE,
                    "Permission mutation succeeded without a direct permission row");
        }
        return new PermissionGrant(
                granted.getId(), granted.getUserId(), granted.getResource(),
                granted.getAction(), granted.getGrantedBy(), granted.getGrantedAt(),
                granted.getExpiresAt());
    }

    @Override
    public boolean revokePermission(String userId, String action, String resource, String actorId) {
        AuthAccountRecord account = requireAccount(userId);
        TraceMetadata trace = currentTrace();
        String stableKey = stableKey("auth-http-permission", trace.traceId(), "REVOKE",
                account.id(), action, resource);
        PermissionMutationCommand command = new PermissionMutationCommand(
                commandId(stableKey),
                IdMetadata.of(stableKey, null),
                actor(actorId, "revoke permission"),
                trace,
                account.id(),
                PermissionMutationCommand.Operation.REVOKE,
                requireText(action, "action"),
                requireText(resource, "resource"),
                null,
                account.authzVersion(),
                "HTTP permission administration");
        RpcResult<AuthorizationMutationDTO> result = receiptExecutor.execute(
                "AuthorizationMutationService", "mutatePermission", command,
                AuthorizationMutationDTO.class,
                ignored -> authorizationMutationWorkflow.mutatePermission(command));
        return requireSuccess(result).changed();
    }

    private AuthAccountRecord requireAccount(String userId) {
        String accountId = requireText(userId, "user id");
        return authAccountPort.findById(accountId.trim())
                .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.ACCOUNT_NOT_FOUND));
    }

    private UserPermission findDirectPermission(String accountId, String action, String resource) {
        List<UserPermission> permissions = permissionService.getUserPermissions(accountId);
        if (permissions == null) {
            return null;
        }
        return permissions.stream()
                .filter(permission -> permission != null)
                .filter(permission -> action.equalsIgnoreCase(permission.getAction()))
                .filter(permission -> resource.equalsIgnoreCase(permission.getResource()))
                .findFirst()
                .orElse(null);
    }

    private ActorDelegation actor(String actorId, String rationale) {
        String actorType = currentUserProvider.hasRole("SUPER_ADMIN")
                ? "SUPER_ADMIN" : "ADMIN";
        return new ActorDelegation(actorType, requireText(actorId, "actor id"),
                actorId, rationale);

    }
    private static TraceMetadata currentTrace() {
        String traceId = TraceIdUtil.current();
        return new TraceMetadata(
                traceId == null || traceId.isBlank()
                        ? "t-" + UUID.randomUUID() : traceId,
                null, null, null);
    }

    private static String stableKey(String prefix, String... values) {
        return prefix + "-" + java.util.Arrays.stream(values)
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .reduce((left, right) -> left + "-" + right)
                .orElse("");
    }

    private static String commandId(String stableKey) {
        return UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, field + " is required");
        }
        return value;
    }

    private static <T> T requireSuccess(RpcResult<T> result) {
        if (result != null && result.success() && result.data() != null) {
            return result.data();
        }
        int code = result == null || result.error() == null
                ? AuthErrorCode.UNEXPECTED_AUTH_STATE.code() : result.error().code();
        if (code == AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
            throw new AuthBusinessException(AuthErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (code == AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT.code()
                || code == AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()) {
            throw new AuthBusinessException(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT);
        }
        if (code == BaseErrorCode.VALIDATION_FAILED.code()
                || code == AuthErrorCode.INVALID_ACCOUNT_REQUEST.code()) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED);
        }
        if (code == BaseErrorCode.FORBIDDEN.code()) {
            throw new AuthBusinessException(BaseErrorCode.FORBIDDEN);
        }
        throw new AuthBusinessException(AuthErrorCode.UNEXPECTED_AUTH_STATE);
    }
}

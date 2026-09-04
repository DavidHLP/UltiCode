package com.ulticode.auth.authorization;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.PermissionVocabulary;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deep Auth implementation for one direct permission delta. */
@Service
@RequiredArgsConstructor
public class DefaultAuthorizationMutationWorkflow implements AuthorizationMutationWorkflow {

    private final AuthAccountPort authAccountPort;
    private final PermissionService permissionService;
    private final AuditSinkPort auditSinkPort;
    private final PermissionVocabulary permissionVocabulary;

    @Override
    @Transactional
    public RpcResult<AuthorizationMutationDTO> mutatePermission(
            PermissionMutationCommand command) {
        String traceId = traceId(command);
        Optional<AuthAccountRecord> account = authAccountPort.findById(command.accountId());
        if (account.isEmpty()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        long currentVersion = account.get().authzVersion();
        if (currentVersion != command.expectedVersion()) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }
        if (permissionVocabulary.isSuperAdminOnlyPermission(
                command.action(), command.resource())
                && !"SUPER_ADMIN".equalsIgnoreCase(command.actor().actorType())) {
            return RpcResult.failure(
                    com.ulticode.common.error.BaseErrorCode.FORBIDDEN, traceId);
        }
        LocalDateTime requestedExpiry = command.expiresAt() == null
                ? null : command.expiresAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        List<UserPermission> directPermissions = permissionService.getUserPermissions(
                command.accountId());
        if (directPermissions == null) {
            throw new AuthBusinessException(
                    AuthErrorCode.UNEXPECTED_AUTH_STATE,
                    "Direct permission read returned null");
        }
        UserPermission existing = findPermission(directPermissions, command);
        LocalDateTime previousExpiry = existing == null ? null : existing.getExpiresAt();
        if (command.operation() == PermissionMutationCommand.Operation.REVOKE) {
            boolean removed = permissionService.revokePermission(
                    command.accountId(), command.action(), command.resource());
            if (!removed) {
                return success(command, currentVersion, false, null, traceId);
            }
            RpcResult<AuthorizationMutationDTO> result = commit(
                    command, null, currentVersion, traceId);
            recordAudit(command, previousExpiry, null, false);
            return result;
        }
        if (existing != null
                && java.util.Objects.equals(existing.getExpiresAt(), requestedExpiry)) {
            return success(command, currentVersion, false, existing.getExpiresAt(), traceId);
        }

        UserPermission granted = permissionService.assignPermission(
                command.accountId(), command.action(), command.resource(),
                requestedExpiry, command.actorId());
        if (granted == null) {
            throw new AuthBusinessException(
                    AuthErrorCode.UNEXPECTED_AUTH_STATE,
                    "Permission grant returned null");
        }
        RpcResult<AuthorizationMutationDTO> result = commit(
                command, granted.getExpiresAt(), currentVersion, traceId);
        recordAudit(command, previousExpiry, granted.getExpiresAt(), true);
        return result;
    }

    private RpcResult<AuthorizationMutationDTO> commit(
            PermissionMutationCommand command,
            LocalDateTime expiresAt,
            long currentVersion,
            String traceId) {
        if (!authAccountPort.bumpAuthzVersionIfExpected(
                command.accountId(), currentVersion)) {
            throw new AuthBusinessException(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT);
        }
        return success(command, currentVersion + 1, true, expiresAt, traceId);
    }

    private void recordAudit(
            PermissionMutationCommand command,
            LocalDateTime previousExpiry,
            LocalDateTime nextExpiry,
            boolean granted) {
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("action", command.action());
        oldValues.put("resource", command.resource());
        oldValues.put("source", "direct");
        if (previousExpiry != null) {
            oldValues.put("expiresAt", previousExpiry.toString());
        }
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("action", command.action());
        newValues.put("resource", command.resource());
        newValues.put("source", "direct");
        newValues.put("operation", granted ? "GRANT" : "REVOKE");
        if (nextExpiry != null) {
            newValues.put("expiresAt", nextExpiry.toString());
        }
        auditSinkPort.log(
                command.actorId(), command.accountId(), "AUTHORIZATION_CHANGED",
                "USER_AUTHORIZATION", command.accountId(), oldValues, newValues,
                "unknown", null);
    }

    private static UserPermission findPermission(
            List<UserPermission> permissions, PermissionMutationCommand command) {
        return permissions.stream()
                .filter(permission -> permission != null)
                .filter(permission -> command.action().equalsIgnoreCase(permission.getAction()))
                .filter(permission -> command.resource().equalsIgnoreCase(permission.getResource()))
                .findFirst()
                .orElse(null);
    }

    private static RpcResult<AuthorizationMutationDTO> success(
            PermissionMutationCommand command,
            long version,
            boolean changed,
            LocalDateTime expiresAt,
            String traceId) {
        return RpcResult.success(
                new AuthorizationMutationDTO(
                        command.accountId(), command.operation().name(), command.action(),
                        command.resource(), "direct",
                        expiresAt == null ? null : expiresAt.atOffset(ZoneOffset.UTC),
                        version, changed),
                traceId);
    }

    private static String traceId(PermissionMutationCommand command) {
        if (command.trace() == null || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return "t-system";
        }
        return command.trace().traceId();
    }
}

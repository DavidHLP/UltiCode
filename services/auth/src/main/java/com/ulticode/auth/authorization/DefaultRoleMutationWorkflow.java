package com.ulticode.auth.authorization;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Deep Auth implementation for a role-only mutation. */
@Service
@RequiredArgsConstructor
public class DefaultRoleMutationWorkflow implements RoleMutationWorkflow {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "USER", "MODERATOR", "ADMIN", "SUPER_ADMIN");

    private final AuthAccountPort authAccountPort;
    private final AuditSinkPort auditSinkPort;

    @Override
    @Transactional
    public RpcResult<AccountMutationDTO> changeRole(ChangeRoleCommand command) {
        String traceId = traceId(command);
        Optional<AuthAccountRecord> account = authAccountPort.findById(command.accountId());
        if (account.isEmpty()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        AuthAccountRecord current = account.get();
        if (current.authzVersion() != command.expectedVersion()) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }
        String role = command.role().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(role)) {
            return RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, traceId);
        }
        if (role.equalsIgnoreCase(current.role())) {
            return RpcResult.success(toMutation(current), traceId);
        }
        if (!authAccountPort.updateAccountIfVersion(
                command.accountId(), Boolean.TRUE.equals(current.isActive()),
                Boolean.TRUE.equals(current.isBanned()), role, current.authzVersion())) {
            throw new AuthBusinessException(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT);
        }
        AuthAccountRecord updated = new AuthAccountRecord(
                current.id(), current.username(), current.email(), current.password(), role,
                current.isActive(), current.isBanned(), current.bannedUntil(),
                current.joinedAt(), current.authzVersion() + 1);
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("role", current.role());
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("role", role);
        newValues.put("authzVersion", updated.authzVersion());
        auditSinkPort.log(
                command.actor().actorId(), command.accountId(), "AUTHORIZATION_CHANGED",
                "USER_AUTHORIZATION", command.accountId(), oldValues, newValues,
                "unknown", null);
        return RpcResult.success(toMutation(updated), traceId);
    }

    private static AccountMutationDTO toMutation(AuthAccountRecord account) {
        return new AccountMutationDTO(
                account.id(), account.username(), account.email(), account.role(),
                Boolean.TRUE.equals(account.isActive()), Boolean.TRUE.equals(account.isBanned()),
                account.authzVersion(), false);
    }

    private static String traceId(ChangeRoleCommand command) {
        if (command.trace() == null || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return "t-system";
        }
        return command.trace().traceId();
    }
}

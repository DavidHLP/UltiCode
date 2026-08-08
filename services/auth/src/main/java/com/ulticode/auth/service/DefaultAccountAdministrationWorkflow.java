package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.rpc.RpcResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Default Auth-owned implementation of {@link AccountAdministrationWorkflow}. */
@Service
public class DefaultAccountAdministrationWorkflow implements AccountAdministrationWorkflow {

    private final AuthAccountPort authAccountPort;
    private final PermissionService permissionService;

    public DefaultAccountAdministrationWorkflow(
            AuthAccountPort authAccountPort,
            PermissionService permissionService) {
        this.authAccountPort = authAccountPort;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional
    public RpcResult<AccountStateDTO> changeState(ChangeAccountStateCommand command) {
        String traceId = traceId(command);
        Optional<AuthAccountRecord> accountOpt = authAccountPort.findById(command.accountId());
        if (accountOpt.isEmpty()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        AuthAccountRecord currentAccount = accountOpt.get();

        if (currentAccount.authzVersion() != command.expectedVersion()) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        boolean targetActive = Boolean.TRUE.equals(currentAccount.isActive());
        boolean targetBanned = Boolean.TRUE.equals(currentAccount.isBanned());
        switch (command.action()) {
            case DISABLE -> targetActive = false;
            case ENABLE -> targetActive = true;
            case BAN -> targetBanned = true;
            case UNBAN -> targetBanned = false;
        }

        boolean updated = authAccountPort.updateAccountIfVersion(
                command.accountId(),
                targetActive,
                targetBanned,
                currentAccount.role(),
                command.expectedVersion());
        if (!updated) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        return RpcResult.success(
                new AccountStateDTO(
                        command.accountId(),
                        targetActive,
                        targetBanned,
                        command.expectedVersion() + 1),
                traceId);
    }

    @Override
    @Transactional
    public RpcResult<AuthorizationSnapshotDTO> changeAuthorization(
            ChangeAuthorizationCommand command) {
        String traceId = traceId(command);
        Optional<AuthAccountRecord> accountOpt = authAccountPort.findById(command.accountId());
        if (accountOpt.isEmpty()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        AuthAccountRecord currentAccount = accountOpt.get();

        if (currentAccount.authzVersion() != command.expectedVersion()) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        boolean updated = authAccountPort.updateAccountIfVersion(
                command.accountId(),
                Boolean.TRUE.equals(currentAccount.isActive()),
                Boolean.TRUE.equals(currentAccount.isBanned()),
                command.role(),
                command.expectedVersion());
        if (!updated) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        Set<String> newPermissions = command.permissions();
        List<UserPermission> existingPerms = permissionService.getUserPermissions(command.accountId());
        Set<String> existingPermStrings = existingPerms == null
                ? Collections.emptySet()
                : existingPerms.stream()
                        .map(permission -> permission.getAction() + ":" + permission.getResource())
                        .collect(Collectors.toSet());

        for (String permission : newPermissions) {
            if (!existingPermStrings.contains(permission)) {
                String[] parts = permission.split(":", 2);
                if (parts.length == 2) {
                    permissionService.assignPermission(command.accountId(), parts[0], parts[1], null);
                }
            }
        }
        for (String oldPermission : existingPermStrings) {
            if (!newPermissions.contains(oldPermission)) {
                String[] parts = oldPermission.split(":", 2);
                if (parts.length == 2) {
                    permissionService.revokePermission(command.accountId(), parts[0], parts[1]);
                }
            }
        }

        List<String> finalPermStrings = permissionService.getUserPermissionStrings(command.accountId());
        Set<String> finalPermissions = finalPermStrings == null
                ? Collections.emptySet()
                : new HashSet<>(finalPermStrings);
        return RpcResult.success(
                new AuthorizationSnapshotDTO(
                        command.accountId(),
                        command.role(),
                        finalPermissions,
                        command.expectedVersion() + 1),
                traceId);
    }

    private static String traceId(com.ulticode.auth.api.command.WriteCommand command) {
        if (command == null || command.trace() == null
                || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return "t-system";
        }
        return command.trace().traceId();
    }
}

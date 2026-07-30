package com.ulticode.auth.dubbo.provider;

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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AccountAdministrationEngine {

    private final AuthAccountPort authAccountPort;
    private final PermissionService permissionService;

    public AccountAdministrationEngine(AuthAccountPort authAccountPort, PermissionService permissionService) {
        this.authAccountPort = authAccountPort;
        this.permissionService = permissionService;
    }

    @Transactional
    public RpcResult<AccountStateDTO> changeState(ChangeAccountStateCommand command, String traceId) {
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
                command.expectedVersion()
        );

        if (!updated) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        AccountStateDTO dto = new AccountStateDTO(
                command.accountId(),
                targetActive,
                targetBanned,
                command.expectedVersion() + 1
        );

        return RpcResult.success(dto, traceId);
    }

    @Transactional
    public RpcResult<AuthorizationSnapshotDTO> changeAuthorization(ChangeAuthorizationCommand command, String traceId) {
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
                command.expectedVersion()
        );

        if (!updated) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        Set<String> newPermissions = command.permissions();
        if (newPermissions != null) {
            List<UserPermission> existingPerms = permissionService.getUserPermissions(command.accountId());
            Set<String> existingPermStrings = (existingPerms == null) ? Collections.emptySet() :
                    existingPerms.stream()
                            .map(p -> p.getAction() + ":" + p.getResource())
                            .collect(Collectors.toSet());

            for (String permStr : newPermissions) {
                if (!existingPermStrings.contains(permStr)) {
                    String[] parts = permStr.split(":", 2);
                    if (parts.length == 2) {
                        permissionService.assignPermission(command.accountId(), parts[0], parts[1], null);
                    }
                }
            }
            for (String oldPerm : existingPermStrings) {
                if (!newPermissions.contains(oldPerm)) {
                    String[] parts = oldPerm.split(":", 2);
                    if (parts.length == 2) {
                        permissionService.revokePermission(command.accountId(), parts[0], parts[1]);
                    }
                }
            }
        }

        List<String> finalPermStrings = permissionService.getUserPermissionStrings(command.accountId());
        Set<String> finalPermissions = (finalPermStrings == null) ? Collections.emptySet() : new HashSet<>(finalPermStrings);

        AuthorizationSnapshotDTO snapshot = new AuthorizationSnapshotDTO(
                command.accountId(),
                command.role(),
                finalPermissions,
                command.expectedVersion() + 1
        );

        return RpcResult.success(snapshot, traceId);
    }
}

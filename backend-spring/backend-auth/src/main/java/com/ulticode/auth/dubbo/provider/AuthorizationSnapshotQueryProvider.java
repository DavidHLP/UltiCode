package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@DubboService(version = "1.0.0")
public class AuthorizationSnapshotQueryProvider implements AuthorizationSnapshotService {

    private final AuthAccountPort authAccountPort;
    private final PermissionService permissionService;

    public AuthorizationSnapshotQueryProvider(AuthAccountPort authAccountPort, PermissionService permissionService) {
        this.authAccountPort = authAccountPort;
        this.permissionService = permissionService;
    }

    @Override
    public RpcResult<AuthorizationSnapshotDTO> getSnapshot(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-system");
        }
        Optional<AuthAccountRecord> accountOpt = authAccountPort.findById(accountId);
        if (accountOpt.isEmpty()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-system");
        }
        AuthAccountRecord account = accountOpt.get();
        List<String> permStrings = permissionService.getUserPermissionStrings(account.id());
        Set<String> permissions = (permStrings == null) ? Collections.emptySet() : new HashSet<>(permStrings);

        AuthorizationSnapshotDTO snapshot = new AuthorizationSnapshotDTO(
                account.id(),
                account.role(),
                permissions,
                account.authzVersion()
        );
        return RpcResult.success(snapshot, "t-system");
    }

    @Override
    public RpcResult<List<AuthorizationSnapshotDTO>> batchGetSnapshot(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), "t-system");
        }
        Set<String> validIds = accountIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        if (validIds.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), "t-system");
        }

        List<AuthAccountRecord> records = authAccountPort.findByIds(validIds);
        if (records == null || records.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), "t-system");
        }

        Set<String> foundIds = records.stream().map(AuthAccountRecord::id).collect(Collectors.toSet());
        Map<String, List<UserPermission>> batchPermissions = permissionService.getBatchUserPermissions(foundIds);
        if (batchPermissions == null) {
            batchPermissions = Collections.emptyMap();
        }

        Map<String, List<UserPermission>> finalPermissions = batchPermissions;
        List<AuthorizationSnapshotDTO> snapshots = records.stream()
                .map(account -> {
                    List<UserPermission> userPerms = finalPermissions.getOrDefault(account.id(), Collections.emptyList());
                    Set<String> permStrings = userPerms.stream()
                            .map(p -> p.getAction() + ":" + p.getResource())
                            .collect(Collectors.toSet());
                    return new AuthorizationSnapshotDTO(
                            account.id(),
                            account.role(),
                            permStrings,
                            account.authzVersion()
                    );
                })
                .collect(Collectors.toList());

        return RpcResult.success(snapshots, "t-system");
    }
}

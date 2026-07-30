package com.ulticode.auth.dubbo.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.auth.permission.entity.RolePermission;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.RolePermissionMapper;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private final RolePermissionMapper rolePermissionMapper;

    public AuthorizationSnapshotQueryProvider(AuthAccountPort authAccountPort,
                                              PermissionService permissionService,
                                              RolePermissionMapper rolePermissionMapper) {
        this.authAccountPort = authAccountPort;
        this.permissionService = permissionService;
        this.rolePermissionMapper = rolePermissionMapper;
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

        // Fetch the two primitive permission sources ONCE, then derive both
        // the flat Set<String> and the structured List<PermissionEntry> from
        // the same data so the two representations cannot drift.
        List<RolePermission> rolePerms = queryRolePermissions(account.role());
        List<UserPermission> directPerms = permissionService.getUserPermissions(account.id());

        SnapshotData data = buildSnapshotData(rolePerms, directPerms);

        AuthorizationSnapshotDTO snapshot = new AuthorizationSnapshotDTO(
                account.id(),
                account.role(),
                data.permissions(),
                account.authzVersion(),
                data.permissionEntries()
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

        // Batch direct permissions for all found accounts in one query.
        Set<String> foundIds = records.stream().map(AuthAccountRecord::id).collect(Collectors.toSet());
        Map<String, List<UserPermission>> batchDirectPerms = permissionService.getBatchUserPermissions(foundIds);
        if (batchDirectPerms == null) {
            batchDirectPerms = Collections.emptyMap();
        }

        // Batch role permissions: query each distinct role ONCE, cache the
        // result so accounts sharing the same role don't re-query.
        Map<String, List<RolePermission>> rolePermCache = new HashMap<>();
        for (AuthAccountRecord record : records) {
            rolePermCache.computeIfAbsent(record.role(), this::queryRolePermissions);
        }

        Map<String, List<UserPermission>> finalDirectPerms = batchDirectPerms;
        List<AuthorizationSnapshotDTO> snapshots = records.stream()
                .map(account -> {
                    List<RolePermission> rolePerms = rolePermCache.getOrDefault(account.role(), Collections.emptyList());
                    List<UserPermission> directPerms = finalDirectPerms.getOrDefault(account.id(), Collections.emptyList());
                    SnapshotData data = buildSnapshotData(rolePerms, directPerms);
                    return new AuthorizationSnapshotDTO(
                            account.id(),
                            account.role(),
                            data.permissions(),
                            account.authzVersion(),
                            data.permissionEntries()
                    );
                })
                .collect(Collectors.toList());

        return RpcResult.success(snapshots, "t-system");
    }

    // ------------------------------------------------------------------
    // Private helpers — single-source derivation of both representations
    // ------------------------------------------------------------------

    private List<RolePermission> queryRolePermissions(String role) {
        if (role == null || role.isBlank()) {
            return Collections.emptyList();
        }
        return rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRole, role.trim().toUpperCase()));
    }

    /**
     * Builds both the flat {@code Set<String>} and the structured
     * {@code List<PermissionEntry>} from the same two primitive permission
     * sources, ensuring the representations stay consistent.
     *
     * @param rolePerms   role-template permissions (source="role", never expire)
     * @param directPerms directly-granted user permissions (source="direct", may have expiresAt)
     */
    private SnapshotData buildSnapshotData(List<RolePermission> rolePerms,
                                           List<UserPermission> directPerms) {
        Set<String> permissions = new HashSet<>();
        List<PermissionEntry> entries = new ArrayList<>();

        for (RolePermission rp : rolePerms) {
            permissions.add(rp.getAction() + ":" + rp.getResource());
            entries.add(new PermissionEntry(
                    rp.getAction(),
                    rp.getResource(),
                    "role",
                    null));
        }

        for (UserPermission up : directPerms) {
            permissions.add(up.getAction() + ":" + up.getResource());
            entries.add(new PermissionEntry(
                    up.getAction(),
                    up.getResource(),
                    "direct",
                    toOffsetDateTime(up.getExpiresAt())));
        }

        return new SnapshotData(permissions, entries);
    }

    private static OffsetDateTime toOffsetDateTime(java.time.LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atOffset(ZoneOffset.UTC);
    }

    /** Internal carrier for the two derived representations. */
    private record SnapshotData(Set<String> permissions, List<PermissionEntry> permissionEntries) {}
}

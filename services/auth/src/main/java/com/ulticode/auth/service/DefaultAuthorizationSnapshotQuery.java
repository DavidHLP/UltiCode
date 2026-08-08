package com.ulticode.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.permission.entity.RolePermission;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.RolePermissionMapper;
import com.ulticode.auth.permission.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Default Auth-owned implementation of {@link AuthorizationSnapshotQuery}. */
@Service
@RequiredArgsConstructor
public class DefaultAuthorizationSnapshotQuery implements AuthorizationSnapshotQuery {

    private final AuthAccountPort authAccountPort;
    private final PermissionService permissionService;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public Optional<AuthorizationSnapshotDTO> getSnapshot(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return authAccountPort.findById(accountId)
                .map(this::toSnapshot);
    }

    @Override
    public List<AuthorizationSnapshotDTO> batchGetSnapshot(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> validIds = accountIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<AuthAccountRecord> records = authAccountPort.findByIds(validIds);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, AuthAccountRecord> uniqueRecords = new LinkedHashMap<>();
        for (AuthAccountRecord record : records) {
            if (record != null
                    && record.id() != null
                    && validIds.contains(record.id())) {
                uniqueRecords.putIfAbsent(record.id(), record);
            }
        }
        if (uniqueRecords.isEmpty()) {
            return Collections.emptyList();
        }
        List<AuthAccountRecord> uniqueAccounts = List.copyOf(uniqueRecords.values());

        Set<String> foundIds = uniqueRecords.keySet();
        Map<String, List<UserPermission>> directPermissions =
                permissionService.getBatchUserPermissions(foundIds);
        if (directPermissions == null) {
            directPermissions = Collections.emptyMap();
        }

        Map<String, List<RolePermission>> rolePermissions = new HashMap<>();
        for (AuthAccountRecord record : uniqueAccounts) {
            rolePermissions.computeIfAbsent(
                    record.role(), this::queryRolePermissions);
        }

        Map<String, List<UserPermission>> finalDirectPermissions = directPermissions;
        Map<String, List<RolePermission>> finalRolePermissions = rolePermissions;
        return uniqueAccounts.stream()
                .map(account -> toSnapshot(
                        account,
                        finalRolePermissions.getOrDefault(
                                account.role(), Collections.emptyList()),
                        finalDirectPermissions.getOrDefault(
                                account.id(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private AuthorizationSnapshotDTO toSnapshot(AuthAccountRecord account) {
        SnapshotData data = buildSnapshotData(
                queryRolePermissions(account.role()),
                permissionService.getUserPermissions(account.id()));
        return toSnapshot(account, data);
    }

    private AuthorizationSnapshotDTO toSnapshot(
            AuthAccountRecord account,
            List<RolePermission> rolePermissions,
            List<UserPermission> directPermissions) {
        SnapshotData data = buildSnapshotData(rolePermissions, directPermissions);
        return toSnapshot(account, data);
    }

    private AuthorizationSnapshotDTO toSnapshot(
            AuthAccountRecord account,
            SnapshotData data) {
        return new AuthorizationSnapshotDTO(
                account.id(),
                account.role(),
                data.permissions(),
                account.authzVersion(),
                data.permissionEntries());
    }

    private List<RolePermission> queryRolePermissions(String role) {
        if (role == null || role.isBlank()) {
            return Collections.emptyList();
        }
        List<RolePermission> permissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRole, role.trim().toUpperCase()));
        return permissions == null ? Collections.emptyList() : permissions;
    }

    /**
     * Derives the flat permission set and structured entries from the same
     * primitive role/direct permission lists so the two representations cannot
     * drift.
     */
    private SnapshotData buildSnapshotData(
            List<RolePermission> rolePermissions,
            List<UserPermission> directPermissions) {
        Set<String> permissions = new HashSet<>();
        List<PermissionEntry> entries = new ArrayList<>();

        for (RolePermission permission : safeList(rolePermissions)) {
            permissions.add(permission.getAction() + ":" + permission.getResource());
            entries.add(new PermissionEntry(
                    permission.getAction(),
                    permission.getResource(),
                    "role",
                    null));
        }

        for (UserPermission permission : safeList(directPermissions)) {
            permissions.add(permission.getAction() + ":" + permission.getResource());
            entries.add(new PermissionEntry(
                    permission.getAction(),
                    permission.getResource(),
                    "direct",
                    toOffsetDateTime(permission.getExpiresAt())));
        }

        return new SnapshotData(permissions, entries);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atOffset(ZoneOffset.UTC);
    }

    private record SnapshotData(
            Set<String> permissions,
            List<PermissionEntry> permissionEntries) {
    }
}

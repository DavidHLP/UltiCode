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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AuthorizationSnapshotQueryTest {

    private AuthAccountPort authAccountPort;
    private PermissionService permissionService;
    private RolePermissionMapper rolePermissionMapper;
    private AuthorizationSnapshotQuery query;

    @BeforeEach
    void setUp() {
        authAccountPort = mock(AuthAccountPort.class);
        permissionService = mock(PermissionService.class);
        rolePermissionMapper = mock(RolePermissionMapper.class);
        query = new DefaultAuthorizationSnapshotQuery(
                authAccountPort, permissionService, rolePermissionMapper);
    }

    @Test
    @DisplayName("single lookup derives flat and structured mixed permissions from the same sources")
    void singleMixedPermissions() {
        AuthAccountRecord account = account("user-1", "ADMIN", 5L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(account));
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rolePermission("READ", "PROBLEM")));
        UserPermission direct = directPermission(
                "user-1", "DELETE", "CONTEST", LocalDateTime.of(2026, 12, 31, 23, 59));
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of(direct));

        AuthorizationSnapshotDTO snapshot = query.getSnapshot("user-1").orElseThrow();

        assertThat(snapshot.accountId()).isEqualTo("user-1");
        assertThat(snapshot.role()).isEqualTo("ADMIN");
        assertThat(snapshot.version()).isEqualTo(5L);
        assertThat(snapshot.permissions())
                .containsExactlyInAnyOrder("READ:PROBLEM", "DELETE:CONTEST");
        assertThat(snapshot.permissionEntries())
                .anySatisfy(entry -> assertEntry(entry, "READ", "PROBLEM", "role", null))
                .anySatisfy(entry -> assertEntry(
                        entry,
                        "DELETE",
                        "CONTEST",
                        "direct",
                        direct.getExpiresAt().atOffset(ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("single lookup preserves role-only permissions and role source metadata")
    void singleRoleOnlyPermissions() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "ADMIN", 2L)));
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rolePermission("MANAGE_USERS", "USER")));
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of());

        AuthorizationSnapshotDTO snapshot = query.getSnapshot("user-1").orElseThrow();

        assertThat(snapshot.permissions()).containsExactly("MANAGE_USERS:USER");
        assertThat(snapshot.permissionEntries()).singleElement()
                .satisfies(entry -> assertEntry(
                        entry, "MANAGE_USERS", "USER", "role", null));
    }

    @Test
    @DisplayName("single lookup preserves direct-only permissions and expiry metadata")
    void singleDirectOnlyPermissions() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "USER", 3L)));
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
        UserPermission direct = directPermission(
                "user-1", "READ", "SYSTEM", LocalDateTime.of(2027, 1, 1, 0, 0));
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of(direct));

        AuthorizationSnapshotDTO snapshot = query.getSnapshot("user-1").orElseThrow();

        assertThat(snapshot.permissions()).containsExactly("READ:SYSTEM");
        assertThat(snapshot.permissionEntries()).singleElement()
                .satisfies(entry -> assertEntry(
                        entry,
                        "READ",
                        "SYSTEM",
                        "direct",
                        direct.getExpiresAt().atOffset(ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("single lookup omits unknown and blank accounts without permission reads")
    void singleUnknownAndBlank() {
        when(authAccountPort.findById("unknown")).thenReturn(Optional.empty());

        assertThat(query.getSnapshot("unknown")).isEmpty();
        assertThat(query.getSnapshot(" ")).isEmpty();
        verifyNoInteractions(permissionService, rolePermissionMapper);
    }

    @Test
    @DisplayName("empty batch lookup returns an empty list without storage calls")
    void emptyBatch() {
        assertThat(query.batchGetSnapshot(null)).isEmpty();
        assertThat(query.batchGetSnapshot(Set.of())).isEmpty();
        verifyNoInteractions(authAccountPort, permissionService, rolePermissionMapper);
    }

    @Test
    @DisplayName("batch lookup deduplicates ids, omits unknown accounts, and caches shared roles")
    void batchDeduplicatesAndCachesRoles() {
        LinkedHashSet<String> accountIds = new LinkedHashSet<>();
        accountIds.add("user-1");
        accountIds.add("user-1");
        accountIds.add("user-2");
        accountIds.add("unknown");

        AuthAccountRecord user1 = account("user-1", "ADMIN", 5L);
        AuthAccountRecord user2 = account("user-2", "ADMIN", 7L);
        when(authAccountPort.findByIds(Set.of("user-1", "user-2", "unknown")))
                .thenReturn(List.of(user1, user1, user2));
        when(permissionService.getBatchUserPermissions(Set.of("user-1", "user-2")))
                .thenReturn(Map.of("user-1", List.of(), "user-2", List.of()));
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rolePermission("READ", "PROBLEM")));

        List<AuthorizationSnapshotDTO> snapshots = query.batchGetSnapshot(accountIds);

        assertThat(snapshots).extracting(AuthorizationSnapshotDTO::accountId)
                .containsExactlyInAnyOrder("user-1", "user-2");
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.permissions())
                .containsExactly("READ:PROBLEM"));
        verify(rolePermissionMapper, times(1))
                .selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("batch lookup derives mixed, role-only, and direct-only snapshots")
    void batchProjectionVariants() {
        AuthAccountRecord mixed = account("mixed", "ADMIN", 1L);
        AuthAccountRecord roleOnly = account("role-only", "MODERATOR", 2L);
        AuthAccountRecord directOnly = account("direct-only", "USER", 3L);
        when(authAccountPort.findByIds(Set.of("mixed", "role-only", "direct-only")))
                .thenReturn(List.of(mixed, roleOnly, directOnly));
        when(permissionService.getBatchUserPermissions(
                Set.of("mixed", "role-only", "direct-only")))
                .thenReturn(Map.of(
                        "mixed", List.of(directPermission(
                                "mixed", "WRITE", "PROBLEM", null)),
                        "role-only", List.of(),
                        "direct-only", List.of(directPermission(
                                "direct-only", "READ", "SYSTEM", null))));
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(
                        List.of(rolePermission("READ", "PROBLEM")),
                        List.of(rolePermission("MODERATE", "PROBLEM")),
                        List.of());

        List<AuthorizationSnapshotDTO> snapshots = query.batchGetSnapshot(
                Set.of("mixed", "role-only", "direct-only"));
        Map<String, AuthorizationSnapshotDTO> byId = snapshots.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AuthorizationSnapshotDTO::accountId,
                        snapshot -> snapshot));

        assertThat(byId.get("mixed").permissions())
                .containsExactlyInAnyOrder("READ:PROBLEM", "WRITE:PROBLEM");
        assertThat(byId.get("role-only").permissions())
                .containsExactly("MODERATE:PROBLEM");
        assertThat(byId.get("direct-only").permissions()).containsExactly("READ:SYSTEM");
        assertThat(byId.get("mixed").permissionEntries())
                .extracting(PermissionEntry::source)
                .containsExactlyInAnyOrder("role", "direct");
    }

    private AuthAccountRecord account(String id, String role, long version) {
        return new AuthAccountRecord(
                id,
                id + "-name",
                id + "@example.com",
                "secret",
                role,
                true,
                false,
                null,
                null,
                version);
    }

    private RolePermission rolePermission(String action, String resource) {
        RolePermission permission = new RolePermission();
        permission.setAction(action);
        permission.setResource(resource);
        return permission;
    }

    private UserPermission directPermission(
            String userId,
            String action,
            String resource,
            LocalDateTime expiresAt) {
        UserPermission permission = new UserPermission();
        permission.setUserId(userId);
        permission.setAction(action);
        permission.setResource(resource);
        permission.setExpiresAt(expiresAt);
        return permission;
    }

    private void assertEntry(
            PermissionEntry entry,
            String action,
            String resource,
            String source,
            java.time.OffsetDateTime expiresAt) {
        assertThat(entry.action()).isEqualTo(action);
        assertThat(entry.resource()).isEqualTo(resource);
        assertThat(entry.source()).isEqualTo(source);
        assertThat(entry.expiresAt()).isEqualTo(expiresAt);
    }
}

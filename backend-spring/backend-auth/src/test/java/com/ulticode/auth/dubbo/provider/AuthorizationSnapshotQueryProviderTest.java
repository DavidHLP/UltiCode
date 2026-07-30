package com.ulticode.auth.dubbo.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.permission.entity.RolePermission;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.RolePermissionMapper;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationSnapshotQueryProviderTest {

    private AuthAccountPort authAccountPort;
    private PermissionService permissionService;
    private RolePermissionMapper rolePermissionMapper;
    private AuthorizationSnapshotQueryProvider provider;

    @BeforeEach
    void setUp() {
        authAccountPort = mock(AuthAccountPort.class);
        permissionService = mock(PermissionService.class);
        rolePermissionMapper = mock(RolePermissionMapper.class);
        provider = new AuthorizationSnapshotQueryProvider(
                authAccountPort, permissionService, rolePermissionMapper);
    }

    @Test
    @DisplayName("getSnapshot returns snapshot with permissions, entries, and version")
    void getSnapshotSuccess() {
        AuthAccountRecord record = new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "secret",
                "ADMIN", true, false, null, null, 5L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(record));

        // Role permissions (source="role", expiresAt=null)
        RolePermission rolePerm = new RolePermission();
        rolePerm.setRole("ADMIN");
        rolePerm.setAction("READ");
        rolePerm.setResource("PROBLEM");
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rolePerm));

        // Direct user permissions (source="direct", with expiresAt)
        UserPermission directPerm = new UserPermission();
        directPerm.setUserId("user-1");
        directPerm.setAction("DELETE");
        directPerm.setResource("CONTEST");
        LocalDateTime expiry = LocalDateTime.of(2026, 12, 31, 23, 59);
        directPerm.setExpiresAt(expiry);
        when(permissionService.getUserPermissions("user-1"))
                .thenReturn(List.of(directPerm));

        RpcResult<AuthorizationSnapshotDTO> result = provider.getSnapshot("user-1");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().accountId()).isEqualTo("user-1");
        assertThat(result.data().role()).isEqualTo("ADMIN");
        assertThat(result.data().version()).isEqualTo(5L);

        // Flat set: both role and direct merged
        assertThat(result.data().permissions())
                .containsExactlyInAnyOrder("READ:PROBLEM", "DELETE:CONTEST");

        // Structured entries: role entry + direct entry
        assertThat(result.data().permissionEntries()).hasSize(2);
        assertThat(result.data().permissionEntries())
                .anySatisfy(e -> {
                    assertThat(e.action()).isEqualTo("READ");
                    assertThat(e.resource()).isEqualTo("PROBLEM");
                    assertThat(e.source()).isEqualTo("role");
                    assertThat(e.expiresAt()).isNull();
                })
                .anySatisfy(e -> {
                    assertThat(e.action()).isEqualTo("DELETE");
                    assertThat(e.resource()).isEqualTo("CONTEST");
                    assertThat(e.source()).isEqualTo("direct");
                    assertThat(e.expiresAt()).isEqualTo(expiry.atOffset(ZoneOffset.UTC));
                });
    }

    @Test
    @DisplayName("getSnapshot returns failure when account is not found")
    void getSnapshotNotFound() {
        when(authAccountPort.findById("unknown")).thenReturn(Optional.empty());

        RpcResult<AuthorizationSnapshotDTO> result = provider.getSnapshot("unknown");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
    }

    @Test
    @DisplayName("batchGetSnapshot handles empty/null input cleanly without NPE")
    void batchGetSnapshotEmptyInput() {
        RpcResult<List<AuthorizationSnapshotDTO>> result = provider.batchGetSnapshot(null);
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();

        result = provider.batchGetSnapshot(Set.of());
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();
    }

    @Test
    @DisplayName("batchGetSnapshot normalizes null batch permissions to empty map")
    void batchGetSnapshotNullPermissionsNormalized() {
        AuthAccountRecord record = new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "secret",
                "USER", true, false, null, null, 1L);
        when(authAccountPort.findByIds(Set.of("user-1"))).thenReturn(List.of(record));
        when(permissionService.getBatchUserPermissions(Set.of("user-1"))).thenReturn(null);
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        RpcResult<List<AuthorizationSnapshotDTO>> result =
                provider.batchGetSnapshot(Set.of("user-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).permissions()).isEmpty();
        assertThat(result.data().get(0).permissionEntries()).isEmpty();
    }

    @Test
    @DisplayName("batchGetSnapshot populates entries from batch permissions and role cache")
    void batchGetSnapshotWithEntries() {
        AuthAccountRecord admin1 = new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "secret",
                "ADMIN", true, false, null, null, 3L);
        AuthAccountRecord admin2 = new AuthAccountRecord(
                "user-2", "bob", "bob@example.com", "secret",
                "ADMIN", true, false, null, null, 7L);
        when(authAccountPort.findByIds(Set.of("user-1", "user-2")))
                .thenReturn(List.of(admin1, admin2));

        // Shared role template queried once
        RolePermission rolePerm = new RolePermission();
        rolePerm.setRole("ADMIN");
        rolePerm.setAction("MANAGE_USERS");
        rolePerm.setResource("USER");
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rolePerm));

        // Direct perms only for user-1
        UserPermission directPerm = new UserPermission();
        directPerm.setUserId("user-1");
        directPerm.setAction("READ");
        directPerm.setResource("SYSTEM");
        directPerm.setExpiresAt(null);
        when(permissionService.getBatchUserPermissions(Set.of("user-1", "user-2")))
                .thenReturn(java.util.Map.of(
                        "user-1", List.of(directPerm),
                        "user-2", List.of()));

        RpcResult<List<AuthorizationSnapshotDTO>> result =
                provider.batchGetSnapshot(Set.of("user-1", "user-2"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(2);

        // user-1: role perm + direct perm
        AuthorizationSnapshotDTO snap1 = result.data().stream()
                .filter(s -> s.accountId().equals("user-1")).findFirst().orElseThrow();
        assertThat(snap1.permissions())
                .containsExactlyInAnyOrder("MANAGE_USERS:USER", "READ:SYSTEM");
        assertThat(snap1.permissionEntries()).hasSize(2);

        // user-2: role perm only (direct is empty)
        AuthorizationSnapshotDTO snap2 = result.data().stream()
                .filter(s -> s.accountId().equals("user-2")).findFirst().orElseThrow();
        assertThat(snap2.permissions()).containsExactly("MANAGE_USERS:USER");
        assertThat(snap2.permissionEntries()).hasSize(1);
        assertThat(snap2.permissionEntries().get(0).source()).isEqualTo("role");
    }
}

package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationSnapshotQueryProviderTest {

    private AuthAccountPort authAccountPort;
    private PermissionService permissionService;
    private AuthorizationSnapshotQueryProvider provider;

    @BeforeEach
    void setUp() {
        authAccountPort = mock(AuthAccountPort.class);
        permissionService = mock(PermissionService.class);
        provider = new AuthorizationSnapshotQueryProvider(authAccountPort, permissionService);
    }

    @Test
    @DisplayName("getSnapshot returns authorization snapshot with permissions and version")
    void getSnapshotSuccess() {
        AuthAccountRecord record = new AuthAccountRecord("user-1", "alice", "alice@example.com", "secret", "ADMIN", true, false, null, null, 5L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(record));
        when(permissionService.getUserPermissionStrings("user-1")).thenReturn(List.of("READ:PROBLEM", "WRITE:CONTEST"));

        RpcResult<AuthorizationSnapshotDTO> result = provider.getSnapshot("user-1");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().accountId()).isEqualTo("user-1");
        assertThat(result.data().role()).isEqualTo("ADMIN");
        assertThat(result.data().permissions()).containsExactlyInAnyOrder("READ:PROBLEM", "WRITE:CONTEST");
        assertThat(result.data().version()).isEqualTo(5L);
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
        AuthAccountRecord record = new AuthAccountRecord("user-1", "alice", "alice@example.com", "secret", "USER", true, false, null, null, 1L);
        when(authAccountPort.findByIds(Set.of("user-1"))).thenReturn(List.of(record));
        when(permissionService.getBatchUserPermissions(Set.of("user-1"))).thenReturn(null);

        RpcResult<List<AuthorizationSnapshotDTO>> result = provider.batchGetSnapshot(Set.of("user-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).permissions()).isEmpty();
    }
}

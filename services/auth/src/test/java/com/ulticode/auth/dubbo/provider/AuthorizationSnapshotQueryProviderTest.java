package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.service.AuthorizationSnapshotQuery;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorizationSnapshotQueryProviderTest {

    private AuthorizationSnapshotQuery query;
    private AuthorizationSnapshotQueryProvider provider;

    @BeforeEach
    void setUp() {
        query = mock(AuthorizationSnapshotQuery.class);
        provider = new AuthorizationSnapshotQueryProvider(query);
    }

    @Test
    @DisplayName("single snapshot maps the local query DTO without changing its wire shape")
    void getSnapshotMapsSuccess() {
        AuthorizationSnapshotDTO expected = snapshot("user-1");
        when(query.getSnapshot("user-1")).thenReturn(Optional.of(expected));

        RpcResult<AuthorizationSnapshotDTO> result = provider.getSnapshot("user-1");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(expected);
        assertThat(result.traceId()).isEqualTo("t-system");
    }

    @Test
    @DisplayName("unknown single snapshot maps to the existing account-not-found error")
    void getSnapshotMapsUnknown() {
        when(query.getSnapshot("unknown")).thenReturn(Optional.empty());

        RpcResult<AuthorizationSnapshotDTO> result = provider.getSnapshot("unknown");

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
    }

    @Test
    @DisplayName("blank single snapshot is rejected at the transport boundary")
    void getSnapshotRejectsBlank() {
        RpcResult<AuthorizationSnapshotDTO> result = provider.getSnapshot(" ");

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("batch snapshots preserve DTO ordering and metadata")
    void batchGetSnapshotMapsSuccess() {
        List<AuthorizationSnapshotDTO> expected = List.of(
                snapshot("user-1"), snapshot("user-2"));
        Set<String> accountIds = Set.of("user-1", "user-2");
        when(query.batchGetSnapshot(accountIds)).thenReturn(expected);

        RpcResult<List<AuthorizationSnapshotDTO>> result =
                provider.batchGetSnapshot(accountIds);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsExactlyElementsOf(expected);
        assertThat(result.data().get(0).permissionEntries().get(0).source())
                .isEqualTo("direct");
    }

    @Test
    @DisplayName("empty batch is a successful empty response without query calls")
    void batchGetSnapshotEmpty() {
        RpcResult<List<AuthorizationSnapshotDTO>> result =
                provider.batchGetSnapshot(Set.of());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();
        verifyNoInteractions(query);
    }

    private AuthorizationSnapshotDTO snapshot(String accountId) {
        return new AuthorizationSnapshotDTO(
                accountId,
                "ADMIN",
                Set.of("READ:PROBLEM"),
                5L,
                List.of(new PermissionEntry(
                        "READ",
                        "PROBLEM",
                        "direct",
                        OffsetDateTime.of(2026, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC))));
    }
}

package com.ulticode.modules.backup.adapter;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.modules.backup.port.UserLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.apache.dubbo.config.annotation.DubboReference;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdentityQueryServiceAdapter}.
 *
 * <p>Covers the non-throwing contract of {@link UserLookupPort}:
 * <ul>
 *   <li>null and empty input → empty map, no RPC call</li>
 *   <li>duplicate IDs in input → deduplicated via LinkedHashSet before RPC</li>
 *   <li>successful reduction: identity rows → {@code Map<accountId, username>}</li>
 *   <li>unknown IDs omitted from result map</li>
 * </ul>
 *
 * <p>Fails loud for null/failed RPC results, null payloads, null requested
 * account IDs, and rows with null row/account ID/username values.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdentityQueryServiceAdapter")
class IdentityQueryServiceAdapterTest {

    @Mock
    private IdentityQueryService identityQueryService;

    @Captor
    private ArgumentCaptor<Set<String>> idsCaptor;

    private IdentityQueryServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new IdentityQueryServiceAdapter(identityQueryService);
    }

    // ─────────────────────────────────────────────────────────────
    // Non-throwing contract
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null input returns empty map")
    void nullInput_returnsEmptyMap() {
        Map<String, String> result = adapter.findUsernamesByIds(null);
        assertThat(result).isEmpty();
        verifyNoInteractions(identityQueryService);
    }

    @Test
    @DisplayName("empty input returns empty map")
    void emptyInput_returnsEmptyMap() {
        Map<String, String> result = adapter.findUsernamesByIds(Collections.emptyList());
        assertThat(result).isEmpty();
        verifyNoInteractions(identityQueryService);
    }

    @Test
    @DisplayName("duplicates are deduplicated via LinkedHashSet before RPC call")
    void duplicatesAreDeduped() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        // Arrays.asList accepts duplicate elements without throwing
        List<String> withDupes = Arrays.asList(id1, id2, id1, id2);

        when(identityQueryService.batchGetIdentity(anySet()))
                .thenReturn(RpcResult.success(Collections.emptyList(), "trace"));

        adapter.findUsernamesByIds(withDupes);

        verify(identityQueryService).batchGetIdentity(idsCaptor.capture());
        Set<String> captured = idsCaptor.getValue();
        assertThat(captured).containsExactly(id1, id2);
    }

    @Test
    @DisplayName("successful lookup reduces to Map<accountId, username>")
    void successfulLookup_reducesToUsernameMap() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        UserIdentityDTO dto1 = new UserIdentityDTO(id1, "alice", "ADMIN", true, false);
        UserIdentityDTO dto2 = new UserIdentityDTO(id2, "bob", "USER", false, true);

        when(identityQueryService.batchGetIdentity(anySet()))
                .thenReturn(RpcResult.success(List.of(dto1, dto2), "trace"));

        Map<String, String> result = adapter.findUsernamesByIds(List.of(id1, id2));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(id1, "alice", id2, "bob"));
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("unknown IDs are omitted from result map")
    void unknownIdsOmitted() {
        String knownId = UUID.randomUUID().toString();
        String unknownId = UUID.randomUUID().toString();
        UserIdentityDTO dto = new UserIdentityDTO(knownId, "alice", "ADMIN", true, false);

        when(identityQueryService.batchGetIdentity(anySet()))
                .thenReturn(RpcResult.success(List.of(dto), "trace"));

        Map<String, String> result = adapter.findUsernamesByIds(List.of(knownId, unknownId));

        assertThat(result).containsKey(knownId);
        assertThat(result).doesNotContainKey(unknownId);
        assertThat(result).hasSize(1);
        assertThat(result.get(knownId)).isEqualTo("alice");
    }

    // ─────────────────────────────────────────────────────────────
    // Fail-loud on RPC anomalies
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null RpcResult throws BusinessException with IDENTITY_QUERY_FAILED")
    void nullRpcResult_throws() {
        when(identityQueryService.batchGetIdentity(anySet())).thenReturn(null);

        assertThatThrownBy(() -> adapter.findUsernamesByIds(List.of(UUID.randomUUID().toString())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
    }

    @Test
    @DisplayName("failed RpcResult throws BusinessException with IDENTITY_QUERY_FAILED")
    void failedRpcResult_throws() {
        RpcResult<List<UserIdentityDTO>> failedResult = mock(RpcResult.class);
        when(failedResult.success()).thenReturn(false);
        when(failedResult.error()).thenReturn(
                new RpcResult.ErrorPayload("auth", 40401, "ACCOUNT_NOT_FOUND"));

        when(identityQueryService.batchGetIdentity(anySet())).thenReturn(failedResult);

        assertThatThrownBy(() -> adapter.findUsernamesByIds(List.of(UUID.randomUUID().toString())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
    }

    @Test
    @DisplayName("RPC invocation failure throws admin BusinessException")
    void rpcInvocationFailure_throws() {
        when(identityQueryService.batchGetIdentity(anySet()))
                .thenThrow(new IllegalStateException("transport unavailable"));

        assertThatThrownBy(() -> adapter.findUsernamesByIds(List.of(UUID.randomUUID().toString())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
    }

    @Test
    @DisplayName("null success payload throws BusinessException with IDENTITY_QUERY_FAILED")
    void nullPayload_throws() {
        RpcResult<List<UserIdentityDTO>> nullPayloadResult = mock(RpcResult.class);
        when(nullPayloadResult.success()).thenReturn(true);
        when(nullPayloadResult.data()).thenReturn(null);
        when(identityQueryService.batchGetIdentity(anySet())).thenReturn(nullPayloadResult);

        assertThatThrownBy(() -> adapter.findUsernamesByIds(List.of(UUID.randomUUID().toString())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
    }
    @Test
    @DisplayName("null requested accountId fails loud before RPC")
    void nullRequestedAccountId_throws() {
        List<String> ids = new ArrayList<>();
        ids.add(UUID.randomUUID().toString());
        ids.add(null);

        assertThatThrownBy(() -> adapter.findUsernamesByIds(ids))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
        verifyNoInteractions(identityQueryService);
    }

    @Test
    @DisplayName("null row fails loud")
    void nullRow_throws() {
        List<UserIdentityDTO> payload = new ArrayList<>();
        payload.add(null);
        when(identityQueryService.batchGetIdentity(anySet()))
                .thenReturn(RpcResult.success(payload, "trace"));

        assertThatThrownBy(() -> adapter.findUsernamesByIds(List.of(UUID.randomUUID().toString())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
    }

    @Test
    @DisplayName("row with null username fails loud")
    void rowWithNullUsername_throws() {
        String id = UUID.randomUUID().toString();
        UserIdentityDTO badRow = new UserIdentityDTO(id, null, "ADMIN", true, false);
        when(identityQueryService.batchGetIdentity(anySet()))
                .thenReturn(RpcResult.success(List.of(badRow), "trace"));

        assertThatThrownBy(() -> adapter.findUsernamesByIds(List.of(id)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
    }

    @Test
    @DisplayName("Dubbo reference uses the read RPC policy")
    void dubboReference_usesReadPolicy() throws NoSuchFieldException {
        Field field = IdentityQueryServiceAdapter.class.getDeclaredField("identityQueryService");
        DubboReference reference = field.getAnnotation(DubboReference.class);

        assertThat(reference).isNotNull();
        assertThat(reference.group()).isEqualTo("backend-auth");
        assertThat(reference.version()).isEqualTo("1.0.0");
        assertThat(reference.timeout()).isEqualTo(RpcPolicy.QUERY_TIMEOUT_MS);
        assertThat(reference.retries()).isEqualTo(RpcPolicy.QUERY_RETRIES);
        assertThat(reference.check()).isFalse();
    }


    @Test
    @DisplayName("row with null accountId throws BusinessException with IDENTITY_QUERY_FAILED")
    void rowWithNullAccountId_throws() {
        UserIdentityDTO badRow = new UserIdentityDTO(null, "alice", "ADMIN", true, false);

        when(identityQueryService.batchGetIdentity(anySet()))
                .thenReturn(RpcResult.success(List.of(badRow), "trace"));

        assertThatThrownBy(() -> adapter.findUsernamesByIds(List.of(UUID.randomUUID().toString())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.IDENTITY_QUERY_FAILED);
    }
}

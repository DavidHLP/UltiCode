package com.ulticode.core;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Zero-infrastructure unit test for the {@link CoreLocalIdentityQueryAdapter}.
 *
 * <p>Verifies that the adapter delegates to the Auth child context's
 * {@link IdentityQueryService} bean without starting any Spring context
 * or requiring infrastructure.
 */
class CoreLocalIdentityQueryAdapterTest {

    @Test
    void delegatesGetIdentityToAuthChildBean() {
        IdentityQueryService authBean = mock(IdentityQueryService.class);
        UserIdentityDTO dto = new UserIdentityDTO("acc-1", "alice", "USER", true, false);
        when(authBean.getIdentity("acc-1")).thenReturn(RpcResult.success(dto, "t"));

        CoreOwnerContextManager ownerContexts = mock(CoreOwnerContextManager.class);
        when(ownerContexts.bean("auth", IdentityQueryService.class)).thenReturn(authBean);

        CoreLocalIdentityQueryAdapter adapter = new CoreLocalIdentityQueryAdapter(ownerContexts);
        RpcResult<UserIdentityDTO> result = adapter.getIdentity("acc-1");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(dto);
    }

    @Test
    void delegatesBatchGetIdentityToAuthChildBean() {
        IdentityQueryService authBean = mock(IdentityQueryService.class);
        List<UserIdentityDTO> list = List.of(
                new UserIdentityDTO("a", "alice", "USER", true, false),
                new UserIdentityDTO("b", "bob", "ADMIN", true, false));
        when(authBean.batchGetIdentity(Set.of("a", "b"))).thenReturn(RpcResult.success(list, "t"));

        CoreOwnerContextManager ownerContexts = mock(CoreOwnerContextManager.class);
        when(ownerContexts.bean("auth", IdentityQueryService.class)).thenReturn(authBean);

        CoreLocalIdentityQueryAdapter adapter = new CoreLocalIdentityQueryAdapter(ownerContexts);
        RpcResult<List<UserIdentityDTO>> result = adapter.batchGetIdentity(Set.of("a", "b"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsExactlyElementsOf(list);
    }

    @Test
    void delegatesFindActiveAccountIdsToAuthChildBean() {
        IdentityQueryService authBean = mock(IdentityQueryService.class);
        when(authBean.findActiveAccountIds()).thenReturn(RpcResult.success(List.of("a", "b"), "t"));

        CoreOwnerContextManager ownerContexts = mock(CoreOwnerContextManager.class);
        when(ownerContexts.bean("auth", IdentityQueryService.class)).thenReturn(authBean);

        CoreLocalIdentityQueryAdapter adapter = new CoreLocalIdentityQueryAdapter(ownerContexts);
        RpcResult<List<String>> result = adapter.findActiveAccountIds();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsExactly("a", "b");
    }
}

package com.ulticode.admin.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.auth.AccountInfo;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountReadAdapterTest {

    @Mock
    private IdentityQueryService identityQueryService;

    private AccountReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountReadAdapter();
        ReflectionTestUtils.setField(adapter, "identityQueryService", identityQueryService);
    }

    @Test
    void missingIdentityProviderFailsClosed() {
        assertThat(new AccountReadAdapter().findById("user-1")).isEmpty();
    }

    @Test
    void successfulIdentityLookupMapsAccountFacts() {
        when(identityQueryService.getIdentity("user-1"))
                .thenReturn(RpcResult.success(
                        new UserIdentityDTO("user-1", "alice", "ADMIN", true, false), "trace"));

        assertThat(adapter.findById("user-1"))
                .contains(new AccountInfo("user-1", "alice", "ADMIN", true, false));
    }

    @Test
    void nullRpcResultFailsClosed() {
        when(identityQueryService.getIdentity("user-1")).thenReturn(null);

        assertThat(adapter.findById("user-1")).isEmpty();
    }

    @Test
    void rpcFailureFailsClosed() {
        when(identityQueryService.getIdentity("user-1"))
                .thenThrow(new IllegalStateException("transport unavailable"));

        assertThat(adapter.findById("user-1")).isEmpty();
    }
}

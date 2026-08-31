package com.ulticode.app.security;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityBanCheckAdapterTest {

    @Test
    void onlyAnExplicitAuthResultCanReportNotBanned() {
        IdentityQueryService auth = mock(IdentityQueryService.class);
        IdentityBanCheckAdapter adapter = new IdentityBanCheckAdapter();
        ReflectionTestUtils.setField(adapter, "identityQueryService", auth);
        UserIdentityDTO identity = new UserIdentityDTO("user-1", "user", "USER", true, false);
        when(auth.getIdentity("user-1")).thenReturn(RpcResult.success(identity, "trace-test"));

        assertThat(adapter.isBanned("user-1")).isFalse();

        when(auth.getIdentity("user-1")).thenThrow(new IllegalStateException("circuit open"));
        assertThatThrownBy(() -> adapter.isBanned("user-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("verify user ban status");
    }
}

package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserIdentityQueryProviderTest {

    private AuthAccountPort authAccountPort;
    private UserIdentityQueryProvider provider;

    @BeforeEach
    void setUp() {
        authAccountPort = mock(AuthAccountPort.class);
        provider = new UserIdentityQueryProvider(authAccountPort);
    }

    @Test
    @DisplayName("getIdentity returns user identity DTO when account exists")
    void getIdentitySuccess() {
        AuthAccountRecord record = new AuthAccountRecord("user-1", "alice", "alice@example.com", "secret", "USER", true, false, null, null, 3L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(record));

        RpcResult<UserIdentityDTO> result = provider.getIdentity("user-1");

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().accountId()).isEqualTo("user-1");
        assertThat(result.data().username()).isEqualTo("alice");
        assertThat(result.data().role()).isEqualTo("USER");
        assertThat(result.data().active()).isTrue();
        assertThat(result.data().banned()).isFalse();
    }

    @Test
    @DisplayName("getIdentity returns failure when account is not found")
    void getIdentityNotFound() {
        when(authAccountPort.findById("unknown")).thenReturn(Optional.empty());

        RpcResult<UserIdentityDTO> result = provider.getIdentity("unknown");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
    }

    @Test
    @DisplayName("batchGetIdentity returns empty list for empty input without throwing NPE")
    void batchGetIdentityEmptyInput() {
        RpcResult<List<UserIdentityDTO>> result = provider.batchGetIdentity(null);
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();

        result = provider.batchGetIdentity(Set.of());
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();
    }

    @Test
    @DisplayName("batchGetIdentity normalizes null batch results to empty list")
    void batchGetIdentityNullResultNormalized() {
        when(authAccountPort.findByIds(Set.of("user-1"))).thenReturn(null);

        RpcResult<List<UserIdentityDTO>> result = provider.batchGetIdentity(Set.of("user-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEmpty();
    }
}

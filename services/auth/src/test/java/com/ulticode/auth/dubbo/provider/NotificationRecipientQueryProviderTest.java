package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.AuthNotificationRecipientDTO;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationRecipientQueryProviderTest {

    @Test
    void exposesOnlyRecipientFieldsAndGovernanceFlags() {
        AuthAccountPort accounts = mock(AuthAccountPort.class);
        when(accounts.findByIds(Set.of("user-1"))).thenReturn(List.of(new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "hashed-secret", "USER",
                true, false, null, null)));

        RpcResult<List<AuthNotificationRecipientDTO>> result =
                new NotificationRecipientQueryProvider(accounts).findRecipients(Set.of("user-1"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsExactly(
                new AuthNotificationRecipientDTO("user-1", "alice@example.com", true, false));
    }

    @Test
    void providerFailureIsNotConvertedToAnEmptySuccess() {
        AuthAccountPort accounts = mock(AuthAccountPort.class);
        when(accounts.findByIds(Set.of("user-1"))).thenThrow(new IllegalStateException("db down"));

        RpcResult<List<AuthNotificationRecipientDTO>> result =
                new NotificationRecipientQueryProvider(accounts).findRecipients(Set.of("user-1"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotNull();
    }
}

package com.ulticode.notification.recipient;

import com.ulticode.auth.api.dto.AuthNotificationRecipientDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.auth.api.service.NotificationRecipientQueryService;
import com.ulticode.common.rpc.RpcResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DubboUserNotificationReadAdapterTest {

    @Test
    void delegatesRecipientReadsDirectlyToAuth() {
        NotificationRecipientQueryService recipientQuery = mock(NotificationRecipientQueryService.class);
        IdentityQueryService identityQuery = mock(IdentityQueryService.class);
        AuthNotificationRecipientDTO authRecipient = new AuthNotificationRecipientDTO(
                "u1", "u1@example.com", true, false);
        when(recipientQuery.findRecipients(Set.of("u1")))
                .thenReturn(RpcResult.success(List.of(authRecipient), "trace-1"));
        when(identityQuery.findActiveAccountIds())
                .thenReturn(RpcResult.success(List.of("u1", "u1"), "trace-1"));

        DubboUserNotificationReadAdapter adapter =
                new DubboUserNotificationReadAdapter(recipientQuery, identityQuery);

        assertThat(adapter.findById("u1"))
                .isEqualTo(new NotificationRecipientDTO("u1", "u1@example.com", true, false));
        assertThat(adapter.findAllActiveIds()).containsExactly("u1");
    }

    @Test
    void missingAuthProviderIsNotReportedAsAnEmptyRecipient() {
        DubboUserNotificationReadAdapter adapter = new DubboUserNotificationReadAdapter();

        assertThatThrownBy(() -> adapter.findById("u1"))
                .isInstanceOf(IllegalStateException.class);
    }
}

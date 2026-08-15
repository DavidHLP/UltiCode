package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.dto.NotificationRecipientDTO;
import com.ulticode.auth.api.dto.AuthNotificationRecipientDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.auth.api.service.NotificationRecipientQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserNotificationReadProviderTest {

    @Test
    void mapsAuthRecipientProjectionWithoutExposingExtraFields() {
        NotificationRecipientQueryService recipients = mock(NotificationRecipientQueryService.class);
        IdentityQueryService identities = mock(IdentityQueryService.class);
        when(recipients.findRecipients(Set.of("u1"))).thenReturn(RpcResult.success(
                List.of(new AuthNotificationRecipientDTO("u1", "u1@example.com", true, false)),
                "trace"));

        UserNotificationReadProvider provider = provider(recipients, identities);

        assertThat(provider.findById("u1"))
                .isEqualTo(new NotificationRecipientDTO("u1", "u1@example.com", true, false));
    }

    @Test
    void authFailureIsExplicitlyUnavailable() {
        NotificationRecipientQueryService recipients = mock(NotificationRecipientQueryService.class);
        IdentityQueryService identities = mock(IdentityQueryService.class);
        when(recipients.findRecipients(Set.of("u1")))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.UNEXPECTED_AUTH_STATE,
                        "trace"));

        UserNotificationReadProvider provider = provider(recipients, identities);

        assertThatThrownBy(() -> provider.findById("u1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activeIdsAreDeduplicatedAtTheAppBoundary() {
        NotificationRecipientQueryService recipients = mock(NotificationRecipientQueryService.class);
        IdentityQueryService identities = mock(IdentityQueryService.class);
        when(identities.findActiveAccountIds()).thenReturn(
                RpcResult.success(Arrays.asList("u1", "", "u1", null, "u2"), "trace"));

        assertThat(provider(recipients, identities).findAllActiveIds())
                .containsExactly("u1", "u2");
    }

    private static UserNotificationReadProvider provider(
            NotificationRecipientQueryService recipients,
            IdentityQueryService identities) {
        UserNotificationReadProvider provider = new UserNotificationReadProvider();
        ReflectionTestUtils.setField(provider, "recipientQueryService", recipients);
        ReflectionTestUtils.setField(provider, "identityQueryService", identities);
        return provider;
    }
}

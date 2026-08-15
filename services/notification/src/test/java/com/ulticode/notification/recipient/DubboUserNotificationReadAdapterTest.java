package com.ulticode.notification.recipient;

import com.ulticode.app.api.dto.NotificationRecipientDTO;
import com.ulticode.app.api.service.UserNotificationReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DubboUserNotificationReadAdapterTest {

    @Test
    void delegatesRecipientReadsToApp() {
        UserNotificationReadPort app = mock(UserNotificationReadPort.class);
        NotificationRecipientDTO recipient = new NotificationRecipientDTO(
                "u1", "u1@example.com", true, false);
        when(app.findById("u1")).thenReturn(recipient);
        when(app.findAllActiveIds()).thenReturn(List.of("u1"));

        DubboUserNotificationReadAdapter adapter = adapter(app);

        assertThat(adapter.findById("u1")).isEqualTo(recipient);
        assertThat(adapter.findAllActiveIds()).containsExactly("u1");
    }

    @Test
    void missingAppProviderIsNotReportedAsAnEmptyRecipient() {
        DubboUserNotificationReadAdapter adapter = new DubboUserNotificationReadAdapter();

        assertThatThrownBy(() -> adapter.findById("u1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static DubboUserNotificationReadAdapter adapter(UserNotificationReadPort app) {
        DubboUserNotificationReadAdapter adapter = new DubboUserNotificationReadAdapter();
        ReflectionTestUtils.setField(adapter, "appRecipientReadPort", app);
        return adapter;
    }
}

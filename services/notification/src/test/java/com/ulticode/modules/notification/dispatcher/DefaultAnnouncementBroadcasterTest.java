package com.ulticode.modules.notification.dispatcher;

import com.ulticode.app.api.service.UserNotificationReadPort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAnnouncementBroadcasterTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationPreferenceMapper preferenceMapper;

    @Mock
    private UserNotificationReadPort userNotificationReadPort;

    @Mock
    private UuidGenerator uuidGenerator;

    private DefaultAnnouncementBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new DefaultAnnouncementBroadcaster(
                notificationMapper,
                preferenceMapper,
                userNotificationReadPort,
                uuidGenerator,
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("ALL resolves Auth-owned active recipients before inserting rows")
    void allResolvesActiveRecipients() {
        when(userNotificationReadPort.findAllActiveIds()).thenReturn(List.of("user-1", "user-2"));
        when(uuidGenerator.newId()).thenReturn("announcement-1");

        AnnouncementBroadcaster.Outcome outcome = broadcaster.broadcast(
                "Maintenance", "Scheduled maintenance", "SYSTEM_ANNOUNCEMENT",
                NotificationCategory.SYSTEM, "ALL", null, Map.of(), null);

        assertThat(outcome.announcementId()).isEqualTo("announcement-1");
        assertThat(outcome.totalTargets()).isEqualTo(2);
        assertThat(outcome.delivered()).isEqualTo(2);
        assertThat(outcome.suppressed()).isZero();

        ArgumentCaptor<List<Notification>> rows = ArgumentCaptor.forClass(List.class);
        verify(notificationMapper).batchInsert(rows.capture());
        assertThat(rows.getValue()).extracting(Notification::getUserId)
                .containsExactly("user-1", "user-2");
        verify(userNotificationReadPort).findAllActiveIds();
    }

    @Test
    @DisplayName("ALL fails closed when the Auth recipient query returns no users")
    void allFailsClosedWithoutRecipients() {
        when(userNotificationReadPort.findAllActiveIds()).thenReturn(List.of());

        assertThatThrownBy(() -> broadcaster.broadcast(
                "Maintenance", "Scheduled maintenance", "SYSTEM_ANNOUNCEMENT",
                NotificationCategory.SYSTEM, "ALL", null, Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No target users found");

        verify(notificationMapper, never()).batchInsert(org.mockito.ArgumentMatchers.anyList());
    }
}

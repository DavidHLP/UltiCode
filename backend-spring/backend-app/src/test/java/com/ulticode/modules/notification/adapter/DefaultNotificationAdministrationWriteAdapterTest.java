package com.ulticode.modules.notification.adapter;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.notification.dispatcher.AnnouncementBroadcaster;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultNotificationAdministrationWriteAdapterTest {

    private AnnouncementBroadcaster broadcaster;
    private NotificationMapper notificationMapper;
    private Clock clock;
    private DefaultNotificationAdministrationWriteAdapter adapter;

    @BeforeEach
    void setUp() {
        broadcaster = mock(AnnouncementBroadcaster.class);
        notificationMapper = mock(NotificationMapper.class);
        clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC"));

        adapter = new DefaultNotificationAdministrationWriteAdapter(broadcaster, notificationMapper, clock);
    }

    @Test
    @DisplayName("createNotification delegates to AnnouncementBroadcaster and returns view DTO")
    void createNotificationSuccess() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "create notif");
        CreateNotificationCommand command = new CreateNotificationCommand(
                "cmd-1", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "admin-1", "System Maintenance", "Scheduled maintenance tonight",
                "SYSTEM", "SYSTEM", "ALL", null);
        AnnouncementBroadcaster.Outcome outcome = new AnnouncementBroadcaster.Outcome(
                "anc-100", "notif-100", 50, 0, 50);
        when(broadcaster.broadcast(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(outcome);

        NotificationAdminViewDTO dto = adapter.createNotification(command);

        assertThat(dto).isNotNull();
        assertThat(dto.notificationId()).isEqualTo("notif-100");
        assertThat(dto.announcementId()).isEqualTo("anc-100");
        assertThat(dto.title()).isEqualTo("System Maintenance");
    }

    @Test
    @DisplayName("deleteNotification deletes notification by ID")
    void deleteNotificationSuccess() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "del");
        DeleteNotificationCommand command = new DeleteNotificationCommand(
                "cmd-del-1", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "notif-100");

        Notification n = new Notification();
        n.setId("notif-100");
        when(notificationMapper.selectById("notif-100")).thenReturn(n);

        adapter.deleteNotification(command);

        verify(notificationMapper).deleteById("notif-100");
    }
}

package com.ulticode.modules.notification.adapter;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.common.exception.BusinessException;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        org.mockito.ArgumentCaptor<Map<String, Object>> metadata =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(broadcaster).broadcast(any(), any(), any(), any(), any(), any(), metadata.capture(), any());
        assertThat(metadata.getValue())
                .containsEntry("createdBy", "admin-1")
                .containsEntry("isSystemAnnouncement", true);
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
        n.setAnnouncementId("anc-100");
        when(notificationMapper.selectById("notif-100")).thenReturn(n);

        adapter.deleteNotification(command);

        verify(notificationMapper).softDeleteAnnouncement("notif-100", "anc-100");
    }

    @Test
    @DisplayName("deleteNotification rejects a personal notification without an announcement group")
    void deleteNotificationRejectsPersonalNotification() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "del");
        DeleteNotificationCommand command = new DeleteNotificationCommand(
                "cmd-del-personal", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "personal-1");
        Notification n = new Notification();
        n.setId("personal-1");
        n.setAnnouncementId(null);
        when(notificationMapper.selectById("personal-1")).thenReturn(n);

        assertThatThrownBy(() -> adapter.deleteNotification(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System notification not found");
        verify(notificationMapper, never()).softDeleteAnnouncement(any(), any());
    }

    @Test
    @DisplayName("updateNotification updates every copy in the announcement group")
    void updateNotificationSuccess() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "update notif");
        UpdateNotificationCommand command = new UpdateNotificationCommand(
                "cmd-update-1", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "notif-100", "New title", "New body", "ALERT", "marketing");

        Notification n = new Notification();
        n.setId("notif-100");
        n.setAnnouncementId("anc-100");
        n.setCategory(NotificationCategory.SYSTEM.name());
        n.setType("SYSTEM");
        when(notificationMapper.selectById("notif-100")).thenReturn(n);

        NotificationAdminViewDTO dto = adapter.updateNotification(command);

        verify(notificationMapper).updateAnnouncement(
                "notif-100", "anc-100", "SYSTEM", "New title", "New body", "ALERT", "MARKETING");
        assertThat(dto.announcementId()).isEqualTo("anc-100");
        assertThat(dto.title()).isEqualTo("New title");
        assertThat(dto.category()).isEqualTo("MARKETING");

    }

    @Test
    @DisplayName("updateNotification treats blank type as unchanged")
    void updateNotificationSkipsBlankType() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "update notif");
        UpdateNotificationCommand command = new UpdateNotificationCommand(
                "cmd-update-blank-type", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "notif-100", "New title", "New body", "   ", null);

        Notification n = new Notification();
        n.setId("notif-100");
        n.setAnnouncementId("anc-100");
        n.setCategory(NotificationCategory.SYSTEM.name());
        n.setType("SYSTEM");
        when(notificationMapper.selectById("notif-100")).thenReturn(n);

        NotificationAdminViewDTO dto = adapter.updateNotification(command);

        verify(notificationMapper).updateAnnouncement(
                "notif-100", "anc-100", "SYSTEM", "New title", "New body", null, null);
        assertThat(dto.type()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("updateNotification rejects an unknown category before writing")
    void updateNotificationRejectsUnknownCategory() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "update notif");
        UpdateNotificationCommand command = new UpdateNotificationCommand(
                "cmd-update-invalid-category", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "notif-100", "New title", "New body", "ALERT", "BOGUS");

        Notification n = new Notification();
        n.setId("notif-100");
        n.setAnnouncementId("anc-100");
        n.setCategory(NotificationCategory.SYSTEM.name());
        when(notificationMapper.selectById("notif-100")).thenReturn(n);

        assertThatThrownBy(() -> adapter.updateNotification(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid notification category");
        verify(notificationMapper, never()).updateAnnouncement(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateNotification rejects a personal notification without an announcement group")
    void updateNotificationRejectsPersonalNotification() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "update notif");
        UpdateNotificationCommand command = new UpdateNotificationCommand(
                "cmd-update-personal", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "personal-1", "New title", "New body", "ALERT", "SYSTEM");
        Notification n = new Notification();
        n.setId("personal-1");
        n.setAnnouncementId(null);
        when(notificationMapper.selectById("personal-1")).thenReturn(n);

        assertThatThrownBy(() -> adapter.updateNotification(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System notification not found");
        verify(notificationMapper, never()).updateAnnouncement(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createNotification rejects USERS without recipient ids as BAD_REQUEST")
    void createNotificationRejectsEmptyUsersTarget() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "create notif");
        CreateNotificationCommand command = new CreateNotificationCommand(
                "cmd-create-empty-users", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "admin-1", "Title", "Body", "SYSTEM", "SYSTEM", "USERS", List.of());

        assertThatThrownBy(() -> adapter.createNotification(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userIds are required");
        verifyNoInteractions(broadcaster);
    }

    @Test
    @DisplayName("createNotification maps an empty resolved audience to BAD_REQUEST")
    void createNotificationRejectsUnknownUsersAsBadRequest() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "create notif");
        CreateNotificationCommand command = new CreateNotificationCommand(
                "cmd-create-unknown-users", IdMetadata.mint(), actor, TraceMetadata.EMPTY,
                "admin-1", "Title", "Body", "SYSTEM", "SYSTEM", "USERS", List.of("missing"));
        when(broadcaster.broadcast(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("No target users found"));

        assertThatThrownBy(() -> adapter.createNotification(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No target users found");
        verify(notificationMapper, never()).batchInsert(any());
    }
}

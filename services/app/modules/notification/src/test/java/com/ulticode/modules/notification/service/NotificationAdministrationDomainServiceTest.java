package com.ulticode.modules.notification.service;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.notification.port.NotificationAdministrationWritePort;
import com.ulticode.modules.notification.service.impl.NotificationAdministrationDomainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationAdministrationDomainService")
class NotificationAdministrationDomainServiceTest {

    @Mock
    private NotificationAdministrationWritePort writePort;

    private NotificationAdministrationDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new NotificationAdministrationDomainServiceImpl(writePort);
    }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    @Test
    @DisplayName("createNotification delegates to writePort")
    void createNotification() {
        var cmd = new CreateNotificationCommand(
                "cmd-1", IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                "admin-acc-1", "Title", "Content", "ANNOUNCEMENT", "SYSTEM", "ALL", List.of());
        var expected = new NotificationAdminViewDTO("n-1", "a-1", "Title", "ANNOUNCEMENT", "SYSTEM", 1000L);
        when(writePort.createNotification(cmd)).thenReturn(expected);

        var result = domainService.createNotification(cmd);

        assertThat(result).isEqualTo(expected);
        verify(writePort).createNotification(cmd);
    }

    @Test
    @DisplayName("deleteNotification delegates to writePort")
    void deleteNotification() {
        var cmd = new DeleteNotificationCommand(
                "cmd-2", IdMetadata.mint(), actor(), TraceMetadata.EMPTY, "n-1");

        domainService.deleteNotification(cmd);

        verify(writePort).deleteNotification(cmd);
    }

    @Test
    @DisplayName("updateNotification delegates to writePort")
    void updateNotification() {
        var cmd = new UpdateNotificationCommand(
                "cmd-3", IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                "n-1", "Updated Title", "Updated Content", "ANNOUNCEMENT", "SYSTEM");
        var expected = new NotificationAdminViewDTO("n-1", "a-1", "Updated Title", "ANNOUNCEMENT", "SYSTEM", 2000L);
        when(writePort.updateNotification(cmd)).thenReturn(expected);

        var result = domainService.updateNotification(cmd);

        assertThat(result).isEqualTo(expected);
        verify(writePort).updateNotification(cmd);
    }
}

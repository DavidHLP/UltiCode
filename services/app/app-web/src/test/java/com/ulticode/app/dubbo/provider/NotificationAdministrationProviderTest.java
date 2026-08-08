package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.notification.service.NotificationAdministrationDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationAdministrationProvider")
class NotificationAdministrationProviderTest {

    @Mock
    private NotificationAdministrationDomainService domainService;

    private NotificationAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NotificationAdministrationProvider(domainService);
    }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    @Nested
    @DisplayName("createNotification()")
    class Create {

        @Test
        @DisplayName("delegates to domainService and returns admin view DTO")
        void createsNotification() {
            NotificationAdminViewDTO expectedDto = new NotificationAdminViewDTO(
                    "notif-1", "ann-1", "System Maintenance", "SYSTEM", "SYSTEM", 1000L);
            when(domainService.createNotification(any())).thenReturn(expectedDto);

            var cmd = new CreateNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "admin-1", "System Maintenance", "Downtime this Sunday", "SYSTEM",
                    "SYSTEM", "ALL", null);
            RpcResult<NotificationAdminViewDTO> result = provider.createNotification(cmd);

            assertThat(result.success()).isTrue();
            assertThat(result.data().notificationId()).isEqualTo("notif-1");
            assertThat(result.data().title()).isEqualTo("System Maintenance");
            verify(domainService).createNotification(cmd);
        }

        @Test
        @DisplayName("maps BusinessException(NOT_FOUND) to CONTENT_NOT_FOUND")
        void mapsNotFound() {
            when(domainService.createNotification(any()))
                    .thenThrow(new BusinessException(BaseErrorCode.NOT_FOUND, "not found"));
            var cmd = new CreateNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "admin-1", "T", "C", "SYSTEM", "SYSTEM", "ALL", null);
            RpcResult<NotificationAdminViewDTO> result = provider.createNotification(cmd);
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }
    }

    @Nested
    @DisplayName("deleteNotification()")
    class Delete {

        @Test
        @DisplayName("delegates to domainService and returns success")
        void deletes() {
            var cmd = new DeleteNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "notif-1");
            RpcResult<Void> result = provider.deleteNotification(cmd);
            assertThat(result.success()).isTrue();
            verify(domainService).deleteNotification(cmd);
        }
    }

    @Nested
    @DisplayName("updateNotification()")
    class Update {

        @Test
        @DisplayName("delegates to domainService and returns updated view DTO")
        void updatesNotification() {
            NotificationAdminViewDTO expectedDto = new NotificationAdminViewDTO(
                    "notif-1", "ann-1", "Updated Title", "SYSTEM", "SYSTEM", 2000L);
            when(domainService.updateNotification(any())).thenReturn(expectedDto);

            var cmd = new UpdateNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "notif-1", "Updated Title", "Updated content", "SYSTEM", "SYSTEM");
            RpcResult<NotificationAdminViewDTO> result = provider.updateNotification(cmd);
            assertThat(result.success()).isTrue();
            assertThat(result.data().title()).isEqualTo("Updated Title");
            verify(domainService).updateNotification(cmd);
        }
    }
}

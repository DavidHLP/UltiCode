package com.ulticode.app.dubbo.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.security.AdminActorAuthorizer;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationAdministrationProvider")
class NotificationAdministrationProviderTest {

    @Mock
    private NotificationAdministrationDomainService domainService;

    @Mock
    private AdminActorAuthorizer actorAuthorizer;

    @Mock
    private AppCommandReceiptMapper receiptMapper;

    private NotificationAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        when(receiptMapper.insertClaim(any())).thenReturn(1);
        when(receiptMapper.markSuccess(anyString(), anyString())).thenReturn(1);
        CommandReceiptExecutor receiptExecutor = new CommandReceiptExecutor(
                receiptMapper,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
        provider = new NotificationAdministrationProvider(domainService, receiptExecutor, actorAuthorizer);
    }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    @Test
    @DisplayName("rejects a non-admin actor before notification mutation")
    void rejectsNonAdminActor() {
        var command = new CreateNotificationCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                new ActorDelegation("USER", "user-1", "user-1", "spoofed admin"),
                TraceMetadata.EMPTY, "user-1", "T", "C", "SYSTEM", "SYSTEM", "ALL", null);

        RpcResult<NotificationAdminViewDTO> result = provider.createNotification(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verify(domainService, never()).createNotification(any());
        verify(actorAuthorizer, never()).isAuthorized(any());
    }

    @Test
    @DisplayName("rejects an unauthorized admin actor before notification mutation")
    void rejectsUnauthorizedAdminActor() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);
        var command = new CreateNotificationCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                "admin-1", "T", "C", "SYSTEM", "SYSTEM", "ALL", null);

        RpcResult<NotificationAdminViewDTO> result = provider.createNotification(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verify(domainService, never()).createNotification(any());
    }

    @Test
    @DisplayName("rejects a creator account that differs from the authenticated actor")
    void rejectsMismatchedCreatorAccount() {
        var command = new CreateNotificationCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                "user-1", "T", "C", "SYSTEM", "SYSTEM", "ALL", null);

        RpcResult<NotificationAdminViewDTO> result = provider.createNotification(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.BAD_REQUEST.code());
        verify(domainService, never()).createNotification(any());
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

        @Test
        @DisplayName("maps BusinessException(BAD_REQUEST) to BAD_REQUEST")
        void mapsBadRequest() {
            when(domainService.createNotification(any()))
                    .thenThrow(new BusinessException(BaseErrorCode.BAD_REQUEST, "bad request"));
            var cmd = new CreateNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "admin-1", "T", "C", "SYSTEM", "SYSTEM", "ALL", null);

            RpcResult<NotificationAdminViewDTO> result = provider.createNotification(cmd);

            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.BAD_REQUEST.code());
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
    @Test
    @DisplayName("replays a successful create without invoking the domain twice")
    void replaysSuccessfulCreate() throws Exception {
        NotificationAdminViewDTO expected = new NotificationAdminViewDTO(
                "notif-replay", "ann-replay", "Replay", "SYSTEM", "SYSTEM", 2000L);
        when(domainService.createNotification(any())).thenReturn(expected);

        var command = new CreateNotificationCommand(
                "command-replay",
                IdMetadata.of("notification-replay", null),
                actor(),
                TraceMetadata.EMPTY,
                "admin-1",
                "Replay",
                "Body",
                "SYSTEM",
                "SYSTEM",
                "ALL",
                null);
        when(receiptMapper.insertClaim(any())).thenReturn(1, 0);

        RpcResult<NotificationAdminViewDTO> first = provider.createNotification(command);
        assertThat(first.success()).isTrue();

        AppCommandReceiptEntity receipt = new AppCommandReceiptEntity();
        receipt.setId("receipt-replay");
        receipt.setStatus("SUCCESS");
        receipt.setRequestFingerprint(CommandReceiptExecutor.fingerprint(command));
        receipt.setResultPayload(new ObjectMapper().writeValueAsString(expected));
        when(receiptMapper.findByReceiptKey(
                "NotificationAdministrationService", "createNotification", "notification-replay"))
                .thenReturn(receipt);

        RpcResult<NotificationAdminViewDTO> replay = provider.createNotification(command);

        assertThat(replay.success()).isTrue();
        assertThat(replay.data()).isEqualTo(expected);
        verify(domainService, times(1)).createNotification(command);
    }
}

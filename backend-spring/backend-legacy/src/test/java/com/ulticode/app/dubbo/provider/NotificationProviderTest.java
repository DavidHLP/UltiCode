package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.command.BatchRejudgeCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.dto.BatchRejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationAdministrationProvider")
class NotificationAdministrationProviderTest {

    @Mock private AdminNotificationService notificationService;
    private NotificationAdministrationProvider provider;

    @BeforeEach
    void setUp() { provider = new NotificationAdministrationProvider(notificationService); }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    @Nested @DisplayName("createNotification()")
    class Create {
        @Test @DisplayName("maps command to request and returns admin view")
        void createsNotification() {
            AdminNotificationVO vo = new AdminNotificationVO();
            vo.setId("notif-1");
            vo.setTitle("System Maintenance");
            vo.setType("SYSTEM");
            vo.setCategory("SYSTEM");
            vo.setCreatedAt(LocalDateTime.now());
            when(notificationService.createSystemNotification(any(CreateSystemNotificationRequest.class))).thenReturn(vo);

            var cmd = new CreateNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "admin-1", "System Maintenance", "Downtime this Sunday", "SYSTEM",
                    "SYSTEM", "ALL", null);
            RpcResult<NotificationAdminViewDTO> result = provider.createNotification(cmd);

            assertThat(result.success()).isTrue();
            assertThat(result.data().notificationId()).isEqualTo("notif-1");
            assertThat(result.data().title()).isEqualTo("System Maintenance");
        }

        @Test @DisplayName("maps BusinessException(NOT_FOUND) to CONTENT_NOT_FOUND")
        void mapsNotFound() {
            when(notificationService.createSystemNotification(any()))
                    .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "not found"));
            var cmd = new CreateNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "admin-1", "T", "C", "SYSTEM", "SYSTEM", "ALL", null);
            RpcResult<NotificationAdminViewDTO> result = provider.createNotification(cmd);
            assertThat(result.success()).isFalse();
            assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
        }
    }

    @Nested @DisplayName("deleteNotification()")
    class Delete {
        @Test @DisplayName("delegates and returns success")
        void deletes() {
            var cmd = new DeleteNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "notif-1");
            RpcResult<Void> result = provider.deleteNotification(cmd);
            assertThat(result.success()).isTrue();
            verify(notificationService).deleteNotification("notif-1");
        }
    }

    @Nested @DisplayName("updateNotification()")
    class Update {
        @Test @DisplayName("maps command to request and returns updated view")
        void updatesNotification() {
            AdminNotificationVO vo = new AdminNotificationVO();
            vo.setId("notif-1");
            vo.setTitle("Updated Title");
            vo.setType("SYSTEM");
            vo.setCreatedAt(LocalDateTime.now());
            when(notificationService.updateSystemNotification(anyString(), any(UpdateSystemNotificationRequest.class))).thenReturn(vo);

            var cmd = new UpdateNotificationCommand(
                    UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                    "notif-1", "Updated Title", "Updated content", null, null);
            RpcResult<NotificationAdminViewDTO> result = provider.updateNotification(cmd);
            assertThat(result.success()).isTrue();
            assertThat(result.data().title()).isEqualTo("Updated Title");
        }
    }
}

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubmissionAdministrationProvider.batchRejudge")
class SubmissionBatchRejudgeProviderTest {

    @Mock private AdminSubmissionService submissionService;
    private SubmissionAdministrationProvider provider;

    @BeforeEach
    void setUp() { provider = new SubmissionAdministrationProvider(submissionService); }

    @Test @DisplayName("maps batch rejudge result to BatchRejudgeResultDTO")
    void batchRejudges() {
        RejudgeResult rr1 = new RejudgeResult();
        rr1.setSubmissionId("s1");
        rr1.setSuccess(true);
        rr1.setNewStatus("Pending");
        rr1.setRejudgedAt(Instant.now());
        rr1.setRetryCount(1);
        RejudgeResult rr2 = new RejudgeResult();
        rr2.setSubmissionId("s2");
        rr2.setSuccess(false);
        rr2.setError("not found");

        BatchRejudgeResponse resp = new BatchRejudgeResponse();
        resp.setTotal(2);
        resp.setSuccessful(1);
        resp.setFailed(1);
        resp.setResults(List.of(rr1, rr2));
        when(submissionService.batchRejudge(anyList(), anyBoolean())).thenReturn(resp);

        var cmd = new BatchRejudgeCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "test"),
                TraceMetadata.EMPTY, List.of("s1", "s2"), false);
        RpcResult<BatchRejudgeResultDTO> result = provider.batchRejudge(cmd);

        assertThat(result.success()).isTrue();
        assertThat(result.data().total()).isEqualTo(2);
        assertThat(result.data().successful()).isEqualTo(1);
        assertThat(result.data().failed()).isEqualTo(1);
        assertThat(result.data().results()).hasSize(2);
    }
}

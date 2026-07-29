package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.service.NotificationAdministrationService;
import com.ulticode.app.api.dto.BatchRejudgeResultDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.service.SubmissionAdministrationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.admin.service.NotificationCutoverService;
import com.ulticode.modules.admin.service.SubmissionCutoverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationCutoverService + SubmissionCutoverService.batchRejudge")
class NotificationCutoverServiceTest {

    @Mock private AdminNotificationService notificationService;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private NotificationAdministrationService notificationDubbo;
    @Mock private AdminSubmissionService adminSubmissionService;
    @Mock private SubmissionAdministrationService submissionDubbo;

    private NotificationCutoverService notificationCutover;
    private SubmissionCutoverService submissionCutover;

    @BeforeEach
    void setUp() {
        notificationCutover = new NotificationCutoverService(notificationService, currentUserProvider);
        ReflectionTestUtils.setField(notificationCutover, "dubboProvider", notificationDubbo);
        ReflectionTestUtils.setField(notificationCutover, "dubboEnabled", false);

        submissionCutover = new SubmissionCutoverService(adminSubmissionService);
        ReflectionTestUtils.setField(submissionCutover, "dubboProvider", submissionDubbo);
        ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", false);
    }

    // ── NotificationCutoverService ─────────────────────────────

    @Nested @DisplayName("flag=off (Notification)")
    class NotificationLocal {
        @Test @DisplayName("createSystemNotification delegates to local service")
        void createLocal() {
            CreateSystemNotificationRequest req = new CreateSystemNotificationRequest();
            req.setTitle("Test");
            req.setContent("Body");
            req.setType("SYSTEM");
            req.setTarget("ALL");
            AdminNotificationVO vo = new AdminNotificationVO();
            vo.setId("n1");
            when(notificationService.createSystemNotification(req)).thenReturn(vo);

            AdminNotificationVO result = notificationCutover.createSystemNotification(req);
            assertThat(result).isSameAs(vo);
            verify(notificationDubbo, never()).createNotification(any());
        }

        @Test @DisplayName("deleteNotification delegates to local service")
        void deleteLocal() {
            notificationCutover.deleteNotification("n1");
            verify(notificationService).deleteNotification("n1");
            verify(notificationDubbo, never()).deleteNotification(any());
        }
    }

    @Nested @DisplayName("flag=on (Notification)")
    class NotificationDubbo {
        @BeforeEach void flagOn() {
            ReflectionTestUtils.setField(notificationCutover, "dubboEnabled", true);
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        }

        @Test @DisplayName("createSystemNotification writes via Dubbo")
        void createViaDubbo() {
            CreateSystemNotificationRequest req = new CreateSystemNotificationRequest();
            req.setTitle("Test");
            req.setContent("Body");
            req.setType("SYSTEM");
            req.setTarget("ALL");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.success(new NotificationAdminViewDTO("n1", "ann-1", "Test", "SYSTEM", "SYSTEM", 0L), "t-1"));

            AdminNotificationVO result = notificationCutover.createSystemNotification(req);
            verify(notificationDubbo).createNotification(any());
            verify(notificationService, never()).createSystemNotification(any());
            assertThat(result.getId()).isEqualTo("n1");
        }

        @Test @DisplayName("deleteNotification writes via Dubbo")
        void deleteViaDubbo() {
            when(notificationDubbo.deleteNotification(any())).thenReturn(RpcResult.success("t-1"));
            notificationCutover.deleteNotification("n1");
            verify(notificationDubbo).deleteNotification(any());
            verify(notificationService, never()).deleteNotification(anyString());
        }

        @Test @DisplayName("RPC error CONTENT_NOT_FOUND maps to BusinessException")
        void mapsError() {
            when(notificationDubbo.deleteNotification(any())).thenReturn(
                    RpcResult.failure(new RpcResult.ErrorPayload("app", 40401, "not found"), "t-1"));
            assertThatThrownBy(() -> notificationCutover.deleteNotification("n1"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── SubmissionCutoverService.batchRejudge ──────────────────

    @Nested @DisplayName("batchRejudge")
    class BatchRejudge {
        @Test @DisplayName("flag=off delegates to local service")
        void localBatch() {
            BatchRejudgeResponse resp = new BatchRejudgeResponse();
            resp.setTotal(2);
            resp.setSuccessful(2);
            resp.setFailed(0);
            when(adminSubmissionService.batchRejudge(anyList(), anyBoolean())).thenReturn(resp);

            BatchRejudgeResponse result = submissionCutover.batchRejudge(List.of("s1", "s2"), false);

            assertThat(result.getTotal()).isEqualTo(2);
            verify(submissionDubbo, never()).batchRejudge(any());
        }

        @Test @DisplayName("flag=on routes through Dubbo")
        void dubboBatch() {
            ReflectionTestUtils.setField(submissionCutover, "dubboEnabled", true);
            when(submissionDubbo.batchRejudge(any())).thenReturn(
                    RpcResult.success(new BatchRejudgeResultDTO(2, 1, 1,
                            List.of(new RejudgeResultDTO("s1", "Pending", Instant.now().toEpochMilli(), 0))),
                            "t-1"));

            BatchRejudgeResponse result = submissionCutover.batchRejudge(List.of("s1", "s2"), false);

            verify(submissionDubbo).batchRejudge(any());
            verify(adminSubmissionService, never()).batchRejudge(anyList(), anyBoolean());
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getSuccessful()).isEqualTo(1);
            assertThat(result.getFailed()).isEqualTo(1);
            assertThat(result.getResults()).hasSize(1);
        }
    }
}

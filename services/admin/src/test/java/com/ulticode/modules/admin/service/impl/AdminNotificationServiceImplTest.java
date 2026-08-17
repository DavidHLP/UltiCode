package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.notification.api.command.CreateNotificationCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.notification.api.dto.NotificationAdminDTO;
import com.ulticode.notification.api.dto.NotificationAdminViewDTO;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.notification.api.service.NotificationAdministrationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.projection.AdminNotificationProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminNotificationServiceImpl} &mdash; ADMIN-008
 * Dubbo-backed admin system-notification service.
 *
 * <p>Covers the remote administration command path (create / delete /
 * update against {@link NotificationAdministrationService}) with explicit
 * RpcResult error mapping, the read-back VO round-trip through
 * {@link NotificationAdminReadPort}, and the list delegating to
 * {@link AdminNotificationProjection}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminNotificationServiceImpl")
class AdminNotificationServiceImplTest {

    @Mock private AdminNotificationProjection adminNotificationProjection;
    @Mock private NotificationAdminReadPort notificationAdminReadPort;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private NotificationAdministrationService notificationDubbo;

    private AdminNotificationServiceImpl adminNotificationService;

    @BeforeEach
    void setUp() {
        adminNotificationService = new AdminNotificationServiceImpl(
                adminNotificationProjection, notificationAdminReadPort, currentUserProvider);
        ReflectionTestUtils.setField(adminNotificationService, "dubboProvider", notificationDubbo);
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
    }

    private AdminNotificationVO makeVO(String id, String announcementId) {
        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setId(id);
        vo.setAnnouncementId(announcementId);
        vo.setTitle("Title " + id);
        vo.setContent("Body " + id);
        vo.setType("SYSTEM");
        vo.setCategory("SYSTEM");
        return vo;
    }

    private NotificationAdminDTO makeRow(String id, String announcementId) {
        return new NotificationAdminDTO(
                id, announcementId, "Title " + id, "Body " + id,
                "SYSTEM", "SYSTEM", LocalDateTime.of(2026, 7, 1, 0, 0), "admin-1");
    }

    private CreateSystemNotificationRequest baseRequest(List<String> userIds, String category) {
        CreateSystemNotificationRequest r = new CreateSystemNotificationRequest();
        r.setTarget("USERS");
        r.setUserIds(userIds);
        r.setCategory(category);
        r.setType("SYSTEM");
        r.setTitle("Announcement");
        r.setContent("Body");
        return r;
    }

    @Nested
    @DisplayName("listSystemNotifications()")
    class ListSystemNotifications {

        @Test
        @DisplayName("passes query through projection and returns its result")
        void delegatesToProjection() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setPage(2);
            query.setLimit(15);

            AdminNotificationVO vo = makeVO("n-1", "a-1");
            PageResult<AdminNotificationVO> expected = PageResult.of(
                    List.of(vo), 1L, query.getPage(), query.getLimit());
            when(adminNotificationProjection.getSystemNotifications(query))
                    .thenReturn(expected);

            PageResult<AdminNotificationVO> result = adminNotificationService.listSystemNotifications(query);

            assertThat(result).isSameAs(expected);
            verify(adminNotificationProjection).getSystemNotifications(query);
            verify(notificationDubbo, never()).createNotification(any());
        }
    }

    @Nested
    @DisplayName("createSystemNotification()")
    class CreateSystemNotification {

        @Test
        @DisplayName("sends command with actor metadata and returns read-back projection VO")
        void sendsCommandAndReadsBack() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.success(new NotificationAdminViewDTO(
                            "row-1", "ann-1", "Announcement", "SYSTEM", "SYSTEM", 1_752_000_000_000L),
                            "t-1"));
            when(notificationAdminReadPort.selectById("row-1")).thenReturn(makeRow("row-1", "ann-1"));
            AdminNotificationVO expectedVo = makeVO("row-1", "ann-1");
            when(adminNotificationProjection.toAdminVO(any(NotificationAdminDTO.class)))
                    .thenReturn(expectedVo);

            AdminNotificationVO result = adminNotificationService.createSystemNotification(
                    baseRequest(List.of("u1", "u2"), "SYSTEM"));

            assertThat(result).isSameAs(expectedVo);
            verify(notificationDubbo).createNotification(any());
            verify(notificationAdminReadPort).selectById("row-1");
            assertThat(AuditContext.getNewValues())
                    .containsEntry("title", "Announcement")
                    .containsEntry("type", "SYSTEM")
                    .containsEntry("category", "SYSTEM")
                    .containsEntry("target", "USERS");
            assertThat(AuditContext.getEntityId()).isEqualTo("row-1");
        }

        @Test
        @DisplayName("propagates SUPER_ADMIN actor type to the App command")
        void propagatesSuperAdminActorType() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.success(new NotificationAdminViewDTO(
                            "row-1", "ann-1", "Announcement", "SYSTEM", "SYSTEM", 1_752_000_000_000L),
                            "t-1"));
            when(notificationAdminReadPort.selectById("row-1")).thenReturn(makeRow("row-1", "ann-1"));
            AdminNotificationVO expectedVo = makeVO("row-1", "ann-1");
            when(adminNotificationProjection.toAdminVO(any(NotificationAdminDTO.class)))
                    .thenReturn(expectedVo);

            adminNotificationService.createSystemNotification(
                    baseRequest(List.of("u1"), "SYSTEM"));

            ArgumentCaptor<CreateNotificationCommand> commandCaptor =
                    ArgumentCaptor.forClass(CreateNotificationCommand.class);
            verify(notificationDubbo).createNotification(commandCaptor.capture());
            assertThat(commandCaptor.getValue().actor().actorType()).isEqualTo("SUPER_ADMIN");
        }
        @Test
        @DisplayName("preserves a supplied idempotency key in the App command")
        void preservesIdempotencyKey() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.success(new NotificationAdminViewDTO(
                            "row-1", "ann-1", "Announcement", "SYSTEM", "SYSTEM", 1_752_000_000_000L),
                            "t-1"));
            when(notificationAdminReadPort.selectById("row-1")).thenReturn(makeRow("row-1", "ann-1"));
            when(adminNotificationProjection.toAdminVO(any(NotificationAdminDTO.class)))
                    .thenReturn(makeVO("row-1", "ann-1"));

            adminNotificationService.createSystemNotification(
                    baseRequest(List.of("u1"), "SYSTEM"), "retry-1");

            ArgumentCaptor<CreateNotificationCommand> commandCaptor =
                    ArgumentCaptor.forClass(CreateNotificationCommand.class);
            verify(notificationDubbo).createNotification(commandCaptor.capture());
            assertThat(commandCaptor.getValue().idempotency().idempotencyKey())
                    .isEqualTo("retry-1");
        }

        @Test
        @DisplayName("every recipient opted out: falls back to announcement-shaped VO when no row persists")
        void allOptedOutFallsBackToAnnouncementVo() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.success(new NotificationAdminViewDTO(
                            "ann-1", "ann-1", "Announcement", "SYSTEM", "MARKETING", 1_752_000_000_000L),
                            "t-1"));
            when(notificationAdminReadPort.selectById("ann-1")).thenReturn(null);
            AdminNotificationVO announcementVo = makeVO("ann-1", "ann-1");
            when(adminNotificationProjection.buildAnnouncementVO(
                    any(), eq("MARKETING"), eq("ann-1")))
                    .thenReturn(announcementVo);

            AdminNotificationVO result = adminNotificationService.createSystemNotification(
                    baseRequest(List.of("u1", "u2"), "marketing "));

            assertThat(result).isSameAs(announcementVo);
            assertThat(AuditContext.getEntityId()).isEqualTo("ann-1");
        }

        @Test
        @DisplayName("maps RPC BAD_REQUEST to BusinessException")
        void mapsBadRequest() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.failure(new RpcResult.ErrorPayload("app", 40000, "Bad request"), "t-1"));

            assertThatThrownBy(() -> adminNotificationService.createSystemNotification(
                    baseRequest(List.of(), "SYSTEM")))
                    .isInstanceOf(BusinessException.class);
            verify(notificationAdminReadPort, never()).selectById(anyString());
        }


        @Test
        @DisplayName("maps RPC FORBIDDEN to the Admin error contract")
        void mapsForbidden() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.failure(
                            new RpcResult.ErrorPayload(
                                    "app", AppErrorCode.FORBIDDEN.code(), "Forbidden"),
                            "t-1"));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> adminNotificationService.createSystemNotification(
                            baseRequest(List.of("u1"), "SYSTEM")));

            assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("maps RPC idempotency conflict to the Admin conflict contract")
        void mapsIdempotencyConflict() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.failure(
                            new RpcResult.ErrorPayload(
                                    "app", AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code(), "Conflict"),
                            "t-1"));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> adminNotificationService.createSystemNotification(
                            baseRequest(List.of("u1"), "SYSTEM"), "retry-1"));

            assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.CONFLICT);
        }

        @Test
        @DisplayName("maps unknown RPC failure to BusinessException")
        void mapsUnknownFailure() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationDubbo.createNotification(any())).thenReturn(
                    RpcResult.failure(new RpcResult.ErrorPayload("app", 50001, "Unexpected app state"), "t-1"));

            assertThatThrownBy(() -> adminNotificationService.createSystemNotification(
                    baseRequest(List.of("u1"), "SYSTEM")))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("updateSystemNotification()")
    class UpdateSystemNotification {

        @Test
        @DisplayName("throws NOT_FOUND when the notification does not exist and skips the RPC")
        void throwsWhenNotificationNotFound() {
            when(notificationAdminReadPort.selectById("missing")).thenReturn(null);

            UpdateSystemNotificationRequest request = new UpdateSystemNotificationRequest();
            request.setTitle("New title");
            request.setContent("New body");
            request.setType("SYSTEM");

            assertThatThrownBy(() -> adminNotificationService.updateSystemNotification("missing", request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Notification not found");
            verify(notificationDubbo, never()).updateNotification(any());
            verify(adminNotificationProjection, never()).toAdminVO(any(NotificationAdminDTO.class));
        }

        @Test
        @DisplayName("sends update command and returns read-back projection VO")
        void sendsCommandAndReadsBack() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationAdminReadPort.selectById("n-1")).thenReturn(makeRow("n-1", "a-1"));
            when(notificationDubbo.updateNotification(any())).thenReturn(
                    RpcResult.success(new NotificationAdminViewDTO(
                            "n-1", "a-1", "New title", "SYSTEM", "SYSTEM", 1_752_000_000_000L),
                            "t-1"));
            when(notificationAdminReadPort.selectById("n-1")).thenReturn(makeRow("n-1", "a-1"));
            AdminNotificationVO expectedVo = makeVO("n-1", "a-1");
            when(adminNotificationProjection.toAdminVO(any(NotificationAdminDTO.class)))
                    .thenReturn(expectedVo);

            UpdateSystemNotificationRequest request = new UpdateSystemNotificationRequest();
            request.setTitle("New title");
            request.setContent("New body");
            request.setType("SYSTEM");

            AdminNotificationVO result = adminNotificationService.updateSystemNotification("n-1", request);

            assertThat(result).isSameAs(expectedVo);
            verify(notificationDubbo).updateNotification(any());
            assertThat(AuditContext.getEntityId()).isEqualTo("n-1");
        }
        @Test
        @DisplayName("keyed update reaches the owner receipt when the row is already gone")
        void keyedUpdateReplaysFromOwnerReceiptWhenRowIsGone() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationAdminReadPort.selectById("n-1")).thenReturn(null);
            when(notificationDubbo.updateNotification(any())).thenReturn(
                    RpcResult.success(new NotificationAdminViewDTO(
                            "n-1", "a-1", "New title", "SYSTEM", "SYSTEM", 1_752_000_000_000L),
                            "t-1"));

            UpdateSystemNotificationRequest request = new UpdateSystemNotificationRequest();
            request.setTitle("New title");
            request.setContent("New body");
            request.setType("SYSTEM");

            AdminNotificationVO result = adminNotificationService.updateSystemNotification(
                    "n-1", request, "update-retry");

            verify(notificationDubbo).updateNotification(any());
            assertThat(result.getId()).isEqualTo("n-1");
            assertThat(result.getAnnouncementId()).isEqualTo("a-1");
            assertThat(result.getContent()).isEqualTo("New body");
        }


        @Test
        @DisplayName("maps RPC CONTENT_NOT_FOUND to BusinessException")
        void mapsContentNotFound() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationAdminReadPort.selectById("n-1")).thenReturn(makeRow("n-1", "a-1"));
            when(notificationDubbo.updateNotification(any())).thenReturn(
                    RpcResult.failure(new RpcResult.ErrorPayload("app", 40401, "Content not found"), "t-1"));

            UpdateSystemNotificationRequest request = new UpdateSystemNotificationRequest();
            request.setTitle("New title");
            request.setContent("New body");
            request.setType("SYSTEM");

            assertThatThrownBy(() -> adminNotificationService.updateSystemNotification("n-1", request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("deleteNotification()")
    class DeleteNotification {

        @Test
        @DisplayName("throws NOT_FOUND when the notification does not exist and skips the RPC")
        void throwsWhenNotificationNotFound() {
            when(notificationAdminReadPort.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> adminNotificationService.deleteNotification("missing"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Notification not found");
            verify(notificationDubbo, never()).deleteNotification(any());
            verify(adminNotificationProjection, never())
                    .toAdminVO(any(NotificationAdminDTO.class));
            verify(adminNotificationProjection, never())
                    .buildAnnouncementVO(any(), any(), any());
        }

        @Test
        @DisplayName("sends delete command and records old values in audit context")
        void sendsDeleteCommand() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationAdminReadPort.selectById("n-1")).thenReturn(makeRow("n-1", "a-1"));
            when(notificationDubbo.deleteNotification(any())).thenReturn(RpcResult.success("t-1"));

            adminNotificationService.deleteNotification("n-1");

            verify(notificationDubbo).deleteNotification(any());
            assertThat(AuditContext.getOldValues())
                    .containsEntry("title", "Title n-1")
                    .containsEntry("type", "SYSTEM");
        }

        @Test
        @DisplayName("keyed delete reaches the owner receipt when the row is already gone")
        void keyedDeleteReachesOwnerReceiptWhenRowIsGone() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationAdminReadPort.selectById("n-1")).thenReturn(null);
            when(notificationDubbo.deleteNotification(any())).thenReturn(RpcResult.success("t-1"));

            adminNotificationService.deleteNotification("n-1", "delete-retry");

            verify(notificationDubbo).deleteNotification(any());
            assertThat(AuditContext.getEntityId()).isEqualTo("n-1");
        }

        @Test
        @DisplayName("maps RPC CONTENT_NOT_FOUND to BusinessException")
        void mapsContentNotFound() {
            when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
            when(notificationAdminReadPort.selectById("n-1")).thenReturn(makeRow("n-1", "a-1"));
            when(notificationDubbo.deleteNotification(any())).thenReturn(
                    RpcResult.failure(new RpcResult.ErrorPayload("app", 40401, "Content not found"), "t-1"));

            assertThatThrownBy(() -> adminNotificationService.deleteNotification("n-1"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}

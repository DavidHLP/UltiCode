package com.ulticode.modules.admin.service;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminDTO;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.NotificationAdminReadPort;
import com.ulticode.app.api.service.NotificationAdministrationService;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.projection.AdminNotificationProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationCutoverService")
class NotificationCutoverServiceTest {

    @Mock
    private AdminNotificationService notificationService;

    @Mock
    private AdminNotificationProjection projection;

    @Mock
    private NotificationAdminReadPort readPort;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private NotificationAdministrationService dubboProvider;

    private NotificationCutoverService cutoverService;

    @BeforeEach
    void setUp() {
        cutoverService = new NotificationCutoverService(
                notificationService, projection, readPort, currentUserProvider);
        ReflectionTestUtils.setField(cutoverService, "dubboProvider", dubboProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);
    }
    @AfterEach
    void clearAuditContext() {
        AuditContext.clear();
    }

    @Test
    @DisplayName("keeps the supplied key on the default service path")
    void forwardsKeyWhenFlagIsDisabled() {
        CreateSystemNotificationRequest request = request();
        AdminNotificationVO expected = new AdminNotificationVO();
        when(notificationService.createSystemNotification(request, "retry-1"))
                .thenReturn(expected);

        AdminNotificationVO result = cutoverService.createSystemNotification(request, "retry-1");

        assertThat(result).isSameAs(expected);
        verify(notificationService).createSystemNotification(request, "retry-1");
    }

    @Test
    @DisplayName("uses a stable command id when the same key is retried")
    void usesStableCommandIdForRetries() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", true);
        NotificationAdminViewDTO dto = new NotificationAdminViewDTO(
                "row-1", "ann-1", "Title", "SYSTEM", "SYSTEM", 1000L);
        AdminNotificationVO expected = new AdminNotificationVO();
        when(dubboProvider.createNotification(any())).thenReturn(RpcResult.success(dto, "t-1"));
        when(readPort.selectById("row-1")).thenReturn(new NotificationAdminDTO(
                "row-1", "ann-1", "Title", "Body", "SYSTEM", "SYSTEM",
                LocalDateTime.of(2026, 8, 11, 0, 0), "admin-1"));
        when(projection.toAdminVO(any())).thenReturn(expected);

        cutoverService.createSystemNotification(request(), "retry-1");
        cutoverService.createSystemNotification(request(), "retry-1");

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(dubboProvider, org.mockito.Mockito.times(2)).createNotification(captor.capture());
        List<CreateNotificationCommand> commands = captor.getAllValues();
        assertThat(commands.get(0).idempotency().idempotencyKey()).isEqualTo("retry-1");
        assertThat(commands.get(1).idempotency().idempotencyKey()).isEqualTo("retry-1");
        assertThat(AuditContext.getNewValues())
                .containsEntry("title", "Title")
                .containsEntry("type", "SYSTEM")
                .containsEntry("category", "SYSTEM")
                .containsEntry("target", "ALL");
        assertThat(AuditContext.getEntityId()).isEqualTo("row-1");
        assertThat(commands.get(1).commandId()).isEqualTo(commands.get(0).commandId());
    }

    @Test
    @DisplayName("keeps audit annotations on all write entrypoints")
    void keepsAuditAnnotationsOnWriteEntrypoints() throws NoSuchMethodException {
        assertThat(NotificationCutoverService.class
                .getDeclaredMethod("createSystemNotification",
                        CreateSystemNotificationRequest.class, String.class)
                .getAnnotation(Audited.class).action())
                .isEqualTo(AuditVocabulary.CREATE_NOTIFICATION);
        assertThat(NotificationCutoverService.class
                .getDeclaredMethod("deleteNotification", String.class, String.class)
                .getAnnotation(Audited.class).action())
                .isEqualTo(AuditVocabulary.DELETE_NOTIFICATION);
        assertThat(NotificationCutoverService.class
                .getDeclaredMethod("updateSystemNotification",
                        String.class, UpdateSystemNotificationRequest.class, String.class)
                .getAnnotation(Audited.class).action())
                .isEqualTo(AuditVocabulary.UPDATE_NOTIFICATION);
    }

    @Test
    @DisplayName("captures old values for an enabled delete")
    void capturesOldValuesForEnabledDelete() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", true);
        when(readPort.selectById("n-1")).thenReturn(new NotificationAdminDTO(
                "n-1", "a-1", "Title", "Body", "SYSTEM", "SYSTEM",
                LocalDateTime.of(2026, 8, 11, 0, 0), "admin-1"));
        when(dubboProvider.deleteNotification(any())).thenReturn(RpcResult.success("t-1"));

        cutoverService.deleteNotification("n-1", "delete-retry");

        assertThat(AuditContext.getOldValues())
                .containsEntry("title", "Title")
                .containsEntry("type", "SYSTEM");
        assertThat(AuditContext.getEntityId()).isEqualTo("n-1");
    }

    @Test
    @DisplayName("captures old and new values for an enabled update")
    void capturesOldAndNewValuesForEnabledUpdate() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", true);
        when(readPort.selectById("n-1")).thenReturn(new NotificationAdminDTO(
                "n-1", "a-1", "Old title", "Old body", "SYSTEM", "SYSTEM",
                LocalDateTime.of(2026, 8, 11, 0, 0), "admin-1"));
        when(dubboProvider.updateNotification(any())).thenReturn(RpcResult.success(
                new NotificationAdminViewDTO(
                        "n-1", "a-1", "New title", "SYSTEM", "SYSTEM", 1000L),
                "t-1"));
        UpdateSystemNotificationRequest update = new UpdateSystemNotificationRequest();
        update.setTitle("New title");
        update.setContent("New body");
        update.setType("SYSTEM");
        update.setCategory("SYSTEM");

        cutoverService.updateSystemNotification("n-1", update, "update-retry");

        assertThat(AuditContext.getOldValues()).containsEntry("title", "Old title");
        assertThat(AuditContext.getNewValues())
                .containsEntry("title", "New title")
                .containsEntry("type", "SYSTEM");
        assertThat(AuditContext.getEntityId()).isEqualTo("n-1");
    }

    @Test
    @DisplayName("keyed update replays from the owner receipt when the row is gone")
    void keyedUpdateReplaysFromOwnerReceiptWhenRowIsGone() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", true);
        when(readPort.selectById("n-1")).thenReturn(null);
        when(dubboProvider.updateNotification(any())).thenReturn(RpcResult.success(
                new NotificationAdminViewDTO(
                        "n-1", "a-1", "New title", "SYSTEM", "SYSTEM", 1000L),
                "t-1"));
        UpdateSystemNotificationRequest update = new UpdateSystemNotificationRequest();
        update.setTitle("New title");
        update.setContent("New body");
        update.setType("SYSTEM");

        AdminNotificationVO result = cutoverService.updateSystemNotification(
                "n-1", update, "update-retry");

        assertThat(result.getId()).isEqualTo("n-1");
        assertThat(result.getAnnouncementId()).isEqualTo("a-1");
        assertThat(result.getContent()).isEqualTo("New body");
    }

    @Test
    @DisplayName("maps a forbidden App response to the Admin error contract")
    void mapsForbiddenResponse() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", true);
        when(dubboProvider.createNotification(any())).thenReturn(
                RpcResult.failure(
                        new RpcResult.ErrorPayload(
                                "app", AppErrorCode.FORBIDDEN.code(), "Forbidden"),
                        "t-1"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cutoverService.createSystemNotification(request(), "retry-1"));

        assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("maps an idempotency conflict to the Admin conflict contract")
    void mapsIdempotencyConflictResponse() {
        ReflectionTestUtils.setField(cutoverService, "dubboEnabled", true);
        when(dubboProvider.createNotification(any())).thenReturn(
                RpcResult.failure(
                        new RpcResult.ErrorPayload(
                                "app", AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code(), "Conflict"),
                        "t-1"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cutoverService.createSystemNotification(request(), "retry-1"));

        assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.CONFLICT);
    }

    private static CreateSystemNotificationRequest request() {
        CreateSystemNotificationRequest request = new CreateSystemNotificationRequest();
        request.setTitle("Title");
        request.setContent("Body");
        request.setType("SYSTEM");
        request.setCategory("SYSTEM");
        request.setTarget("ALL");
        return request;
    }
}

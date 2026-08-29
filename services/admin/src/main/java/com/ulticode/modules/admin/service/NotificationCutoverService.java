package com.ulticode.modules.admin.service;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.notification.api.command.CreateNotificationCommand;
import com.ulticode.notification.api.command.DeleteNotificationCommand;
import com.ulticode.notification.api.command.UpdateNotificationCommand;
import com.ulticode.notification.api.dto.NotificationAdminDTO;
import com.ulticode.notification.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.notification.api.service.NotificationAdministrationService;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.notification.api.service.NotificationServiceContract;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.projection.AdminNotificationProjection;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-side compatibility adapter for the notification-owner write contract.
 *
 * <p>The feature flag preserves the existing call shape while the enabled path
 * sends the command directly to the notification owner. Both paths use the
 * owner RPC and carry the same client idempotency key; a supplied key is never
 * replaced by a new UUID.
 */
@Service
public class NotificationCutoverService {

    private final AdminNotificationService notificationService;
    private final AdminNotificationProjection adminNotificationProjection;
    private final NotificationAdminReadPort notificationAdminReadPort;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Compatibility constructor for focused tests and legacy in-process callers
     * that exercise only the flag-off delegation path.
     */
    public NotificationCutoverService(
            AdminNotificationService notificationService,
            CurrentUserProvider currentUserProvider) {
        this(notificationService, null, null, currentUserProvider);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationCutoverService(
            AdminNotificationService notificationService,
            AdminNotificationProjection adminNotificationProjection,
            NotificationAdminReadPort notificationAdminReadPort,
            CurrentUserProvider currentUserProvider) {
        this.notificationService = notificationService;
        this.adminNotificationProjection = adminNotificationProjection;
        this.notificationAdminReadPort = notificationAdminReadPort;
        this.currentUserProvider = currentUserProvider;
    }

    @Value("${app.features.notification-dubbo-cutover:false}")
    private boolean dubboEnabled;

    @DubboReference(group = NotificationServiceContract.DUBBO_GROUP,
            version = NotificationServiceContract.DUBBO_VERSION,
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private NotificationAdministrationService dubboProvider;

    public PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO) {
        return notificationService.listSystemNotifications(queryDTO);
    }

    @Audited(action = AuditVocabulary.CREATE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request) {
        return createSystemNotification(request, null);
    }

    @Audited(action = AuditVocabulary.CREATE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public AdminNotificationVO createSystemNotification(
            CreateSystemNotificationRequest request, String idempotencyKey) {
        if (!dubboEnabled) {
            return notificationService.createSystemNotification(request, idempotencyKey);
        }

        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        String category = request.getCategory() == null ? "SYSTEM" : request.getCategory();
        RpcResult<NotificationAdminViewDTO> result = dubboProvider.createNotification(
                new CreateNotificationCommand(
                        commandId("create", idempotency),
                        idempotency,
                        new ActorDelegation(actorType(), actorId, actorId, "admin notification create"),
                        trace(),
                        actorId,
                        request.getTitle(),
                        request.getContent(),
                        request.getType(),
                        request.getCategory(),
                        request.getTarget(),
                        request.getUserIds()));
        if (!result.success()) {
            throw mapError(result);
        }
        NotificationAdminViewDTO dto = result.data();
        String effectiveCategory = dto.category() == null || dto.category().isBlank()
                ? category : dto.category();
        AuditContext.setNewValues(Map.of(
                "title", request.getTitle() != null ? request.getTitle() : "",
                "type", request.getType() != null ? request.getType() : "",
                "category", effectiveCategory,
                "target", request.getTarget() != null ? request.getTarget() : ""
        ));
        AuditContext.setEntityId(dto.notificationId());
        return readBack(dto.notificationId(), dto.announcementId(), request, effectiveCategory);
    }

    @Audited(action = AuditVocabulary.DELETE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public void deleteNotification(String id) {
        deleteNotification(id, null);
    }

    @Audited(action = AuditVocabulary.DELETE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public void deleteNotification(String id, String idempotencyKey) {
        if (!dubboEnabled) {
            notificationService.deleteNotification(id, idempotencyKey);
            return;
        }

        captureOldValues(id);
        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<Void> result = dubboProvider.deleteNotification(
                new DeleteNotificationCommand(
                        commandId("delete", idempotency),
                        idempotency,
                        new ActorDelegation(actorType(), actorId, actorId, "admin notification delete"),
                        trace(),
                        id));
        if (!result.success()) {
            throw mapError(result);
        }
    }

    @Audited(action = AuditVocabulary.UPDATE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public AdminNotificationVO updateSystemNotification(
            String id, UpdateSystemNotificationRequest request) {
        return updateSystemNotification(id, request, null);
    }

    @Audited(action = AuditVocabulary.UPDATE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public AdminNotificationVO updateSystemNotification(
            String id, UpdateSystemNotificationRequest request, String idempotencyKey) {
        if (!dubboEnabled) {
            return notificationService.updateSystemNotification(id, request, idempotencyKey);
        }

        captureOldValues(id);
        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<NotificationAdminViewDTO> result = dubboProvider.updateNotification(
                new UpdateNotificationCommand(
                        commandId("update", idempotency),
                        idempotency,
                        new ActorDelegation(actorType(), actorId, actorId, "admin notification update"),
                        trace(),
                        id,
                        request.getTitle(),
                        request.getContent(),
                        request.getType(),
                        request.getCategory()));
        if (!result.success()) {
            throw mapError(result);
        }
        AuditContext.setNewValues(Map.of(
                "title", request.getTitle() != null ? request.getTitle() : "",
                "type", request.getType() != null ? request.getType() : ""
        ));
        AuditContext.setEntityId(id);
        return readBackUpdate(id, result.data(), request);
    }

    private AdminNotificationVO readBackUpdate(
            String notificationId,
            NotificationAdminViewDTO result,
            UpdateSystemNotificationRequest request) {
        NotificationAdminDTO row = notificationAdminReadPort.selectById(notificationId);
        if (row != null) {
            return adminNotificationProjection.toAdminVO(row);
        }
        if (result == null) {
            return null;
        }
        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setId(result.notificationId() != null ? result.notificationId() : notificationId);
        vo.setAnnouncementId(result.announcementId());
        vo.setTitle(result.title() != null ? result.title() : request.getTitle());
        vo.setContent(request.getContent());
        vo.setType(result.type() != null ? result.type() : request.getType());
        vo.setCategory(result.category() != null ? result.category() : request.getCategory());
        if (result.createdEpochMs() > 0) {
            vo.setCreatedAt(Instant.ofEpochMilli(result.createdEpochMs())
                    .atOffset(ZoneOffset.UTC).toLocalDateTime());
        }
        return vo;
    }

    private void captureOldValues(String id) {
        AuditContext.setEntityId(id);
        if (notificationAdminReadPort == null) {
            return;
        }
        NotificationAdminDTO existing = notificationAdminReadPort.selectById(id);
        if (existing != null) {
            AuditContext.setOldValues(Map.of(
                    "title", existing.title() != null ? existing.title() : "",
                    "type", existing.type() != null ? existing.type() : ""
            ));
        }
    }

    private AdminNotificationVO readBack(
            String notificationId,
            String announcementId,
            CreateSystemNotificationRequest fallbackRequest,
            String category) {
        NotificationAdminDTO row = notificationAdminReadPort.selectById(notificationId);
        if (row == null) {
            if (fallbackRequest != null) {
                return adminNotificationProjection.buildAnnouncementVO(
                        fallbackRequest, category, announcementId);
            }
            return null;
        }
        return adminNotificationProjection.toAdminVO(row);
    }

    private String currentActor() {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(
                    AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return actorId;
    }

    private String actorType() {
        return AdminActors.typeOf(currentUserProvider);
    }

    private static IdMetadata idempotency(String requestedKey) {
        String key = requestedKey == null || requestedKey.isBlank()
                ? UUID.randomUUID().toString() : requestedKey.trim();
        if (key.length() > 120) {
            throw new BusinessException(
                    AdminErrorCode.BAD_REQUEST, "Idempotency-Key must not exceed 120 characters");
        }
        return IdMetadata.of(key, null);
    }

    private static String commandId(String operation, IdMetadata idempotency) {
        return UUID.nameUUIDFromBytes(
                (operation + ":" + idempotency.idempotencyKey()).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static TraceMetadata trace() {
        return new TraceMetadata(TraceIdUtil.current(), null, null, null);
    }

    private static BusinessException mapError(RpcResult<?> result) {
        if (result == null || result.error() == null) {
            return new BusinessException(
                    AdminErrorCode.UNKNOWN_ERROR, "RPC failed without error payload");
        }
        int code = result.error().code();
        if (code == AppErrorCode.BAD_REQUEST.code()) {
            return new BusinessException(AdminErrorCode.BAD_REQUEST, result.error().message());
        }
        if (code == AppErrorCode.UNAUTHORIZED.code()) {
            return new BusinessException(AdminErrorCode.UNAUTHORIZED, result.error().message());
        }
        if (code == AppErrorCode.FORBIDDEN.code()) {
            return new BusinessException(AdminErrorCode.FORBIDDEN, result.error().message());
        }
        if (code == AppErrorCode.CONTENT_NOT_FOUND.code()) {
            return new BusinessException(AdminErrorCode.NOT_FOUND, result.error().message());
        }
        if (code == AppErrorCode.VERSION_CONFLICT.code()
                || code == AppErrorCode.CONTENT_STATE_CONFLICT.code()
                || code == AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()) {
            return new BusinessException(AdminErrorCode.CONFLICT, result.error().message());
        }
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, result.error().message());
    }
}

package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.notification.api.command.CreateNotificationCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.notification.api.command.DeleteNotificationCommand;
import com.ulticode.notification.api.command.UpdateNotificationCommand;
import com.ulticode.notification.api.dto.NotificationAdminDTO;
import com.ulticode.notification.api.dto.NotificationAdminViewDTO;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.notification.api.service.NotificationAdministrationService;
import com.ulticode.notification.api.service.NotificationServiceContract;
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
import com.ulticode.modules.admin.service.AdminNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Admin system-notification service &mdash; ADMIN-008.
 *
 * <p>The legacy in-process write state machine is replaced by remote
 * administration commands against the notification-owner
 * {@link NotificationAdministrationService} provider. Admin no
 * longer imports any notification entity/mapper/dispatcher class:
 * reads go through {@link NotificationAdminReadPort}, writes through the
 * command RPC, and the {@link AdminNotificationProjection} keeps the
 * entity-free VO shape rule (including batch creator enrichment).
 *
 * <p>No local transaction wraps the remote writes (RPC boundary); the
 * {@code @Audited} hook and {@link AuditContext} old/new value capture are
 * preserved. RpcResult failures are mapped to {@link AdminErrorCode}
 * (App {@code 40000} &rarr; {@code BAD_REQUEST}, {@code 40401} &rarr;
 * {@code NOT_FOUND}, anything else &rarr; {@code UNKNOWN_ERROR}).
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private static final String SYSTEM_CATEGORY = "SYSTEM";

    private final AdminNotificationProjection adminNotificationProjection;
    private final NotificationAdminReadPort notificationAdminReadPort;
    private final CurrentUserProvider currentUserProvider;

    @DubboReference(group = NotificationServiceContract.DUBBO_GROUP,
            version = NotificationServiceContract.DUBBO_VERSION,
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private NotificationAdministrationService dubboProvider;

    @Override
    public PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO) {
        return adminNotificationProjection.getSystemNotifications(queryDTO);
    }

    @Override
    public AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request) {
        return createSystemNotification(request, null);
    }

    @Override
    public AdminNotificationVO createSystemNotification(
            CreateSystemNotificationRequest request, String idempotencyKey) {
        String actorId = safeActorId();
        String category = request.getCategory() != null ? request.getCategory() : SYSTEM_CATEGORY;
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<NotificationAdminViewDTO> result = dubboProvider.createNotification(
                new CreateNotificationCommand(
                        commandId("create", idempotency), idempotency,
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
        String effectiveCategory = dto.category() != null && !dto.category().isBlank()
                ? dto.category()
                : category;
        AuditContext.setNewValues(Map.of(
                "title", request.getTitle() != null ? request.getTitle() : "",
                "type", request.getType() != null ? request.getType() : "",
                "category", effectiveCategory,
                "target", request.getTarget() != null ? request.getTarget() : ""
        ));
        AuditContext.setEntityId(dto.notificationId());
        log.info("Created system notification '{}' (announcementId={}) by admin {}",
                request.getTitle(), dto.announcementId(), actorId);
        return readBack(dto.notificationId(), dto.announcementId(), request, effectiveCategory);
    }

    @Override
    public void deleteNotification(String id) {
        deleteNotification(id, null);
    }

    @Override
    public void deleteNotification(String id, String idempotencyKey) {
        AuditContext.setEntityId(id);
        NotificationAdminDTO existing = notificationAdminReadPort.selectById(id);
        if (existing == null && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND, "Notification not found");
        }

        if (existing != null) {
            AuditContext.setOldValues(Map.of(
                    "title", existing.title() != null ? existing.title() : "",
                    "type", existing.type() != null ? existing.type() : ""
            ));
        }
        String actorId = safeActorId();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<Void> result = dubboProvider.deleteNotification(
                new DeleteNotificationCommand(
                        commandId("delete", idempotency), idempotency,
                        new ActorDelegation(actorType(), actorId, actorId, "admin notification delete"),
                        trace(), id));
        if (!result.success()) {
            throw mapError(result);
        }
        log.info("Deleted system notification '{}' by admin {}", id, actorId);
    }

    @Override
    public AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request) {
        return updateSystemNotification(id, request, null);
    }

    @Override
    public AdminNotificationVO updateSystemNotification(
            String id, UpdateSystemNotificationRequest request, String idempotencyKey) {
        NotificationAdminDTO existing = notificationAdminReadPort.selectById(id);
        if (existing == null && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND, "Notification not found");
        }

        if (existing != null) {
            AuditContext.setOldValues(Map.of(
                    "title", existing.title() != null ? existing.title() : "",
                    "type", existing.type() != null ? existing.type() : ""
            ));
        }

        String actorId = safeActorId();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<NotificationAdminViewDTO> result = dubboProvider.updateNotification(
                new UpdateNotificationCommand(
                        commandId("update", idempotency), idempotency,
                        new ActorDelegation(actorType(), actorId, actorId, "admin notification update"),
                        trace(), id,
                        request.getTitle(), request.getContent(),
                        request.getType(), request.getCategory()));
        if (!result.success()) {
            throw mapError(result);
        }

        AuditContext.setNewValues(Map.of(
                "title", request.getTitle() != null ? request.getTitle() : "",
                "type", request.getType() != null ? request.getType() : ""
        ));
        AuditContext.setEntityId(id);
        log.info("Updated system notification '{}' by admin {}", id, actorId);
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


    // ── helpers ────────────────────────────────────────────────

    private String actorType() {
        return AdminActors.typeOf(currentUserProvider);
    }
    /**
     * Re-fetch the full VO via the read port so the HTTP response shape
     * matches the legacy projection (content + creator enrichment).
     * When no row is persisted (every recipient opted out of a broadcast,
     * or the row vanished between write and re-read) fall back to the
     * announcement-shaped VO built from the original create request.
     */
    private AdminNotificationVO readBack(String notificationId,
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
    private static IdMetadata idempotency(String requestedKey) {
        String key = requestedKey == null || requestedKey.isBlank()
                ? UUID.randomUUID().toString()
                : requestedKey.trim();
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

    private String safeActorId() {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return actorId;
    }

    private static BusinessException mapError(RpcResult<?> result) {
        var err = result.error();
        if (err == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "RPC failed without error payload");
        }
        int code = err.code();
        if (code == AppErrorCode.BAD_REQUEST.code()) {
            return new BusinessException(AdminErrorCode.BAD_REQUEST, err.message());
        }
        if (code == AppErrorCode.UNAUTHORIZED.code()) {
            return new BusinessException(AdminErrorCode.UNAUTHORIZED, err.message());
        }
        if (code == AppErrorCode.FORBIDDEN.code()) {
            return new BusinessException(AdminErrorCode.FORBIDDEN, err.message());
        }
        if (code == AppErrorCode.CONTENT_NOT_FOUND.code()) {
            return new BusinessException(AdminErrorCode.NOT_FOUND, err.message());
        }
        if (code == AppErrorCode.VERSION_CONFLICT.code()
                || code == AppErrorCode.CONTENT_STATE_CONFLICT.code()
                || code == AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()) {
            return new BusinessException(AdminErrorCode.CONFLICT, err.message());
        }
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, err.message());
    }
}

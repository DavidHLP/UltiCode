package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.NotificationAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.ZoneOffset;

/**
 * P4-CUTOVER-003: Dubbo Provider implementation of
 * {@link NotificationAdministrationService}.
 *
 * <p>Delegates to {@link AdminNotificationService} which routes through the
 * {@code AnnouncementBroadcaster} seam (architecture-review candidate #4)
 * for recipient resolution, preference filtering, and batch row insert.
 *
 * <p>The {@code notifications} table is App-owned per {@code TABLE_OWNERS.md};
 * the Admin BFF must route writes through this contract so that App is the
 * sole writer.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class NotificationAdministrationProvider implements NotificationAdministrationService {

    private final AdminNotificationService notificationService;

    @Override
    public RpcResult<NotificationAdminViewDTO> createNotification(CreateNotificationCommand command) {
        log.info("NotificationAdministrationProvider.createNotification title={} target={} commandId={} actor={}",
                command.title(), command.target(),
                command.commandId(), command.actor().actorId());
        try {
            CreateSystemNotificationRequest request = new CreateSystemNotificationRequest();
            request.setTitle(command.title());
            request.setContent(command.content());
            request.setType(command.type());
            request.setCategory(command.category());
            request.setTarget(command.target());
            request.setUserIds(command.userIds());

            AdminNotificationVO vo = notificationService.createSystemNotification(request);
            return RpcResult.success(toDto(vo), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("NotificationAdministrationProvider.createNotification unexpected error", e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<Void> deleteNotification(DeleteNotificationCommand command) {
        log.info("NotificationAdministrationProvider.deleteNotification id={} commandId={} actor={}",
                command.notificationId(),
                command.commandId(), command.actor().actorId());
        try {
            notificationService.deleteNotification(command.notificationId());
            return RpcResult.success(command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("NotificationAdministrationProvider.deleteNotification unexpected error id={}",
                    command.notificationId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<NotificationAdminViewDTO> updateNotification(UpdateNotificationCommand command) {
        log.info("NotificationAdministrationProvider.updateNotification id={} commandId={} actor={}",
                command.notificationId(),
                command.commandId(), command.actor().actorId());
        try {
            UpdateSystemNotificationRequest request = new UpdateSystemNotificationRequest();
            request.setTitle(command.title());
            request.setContent(command.content());
            request.setType(command.type());
            request.setCategory(command.category());

            AdminNotificationVO vo = notificationService.updateSystemNotification(
                    command.notificationId(), request);
            return RpcResult.success(toDto(vo), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("NotificationAdministrationProvider.updateNotification unexpected error id={}",
                    command.notificationId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    private static NotificationAdminViewDTO toDto(AdminNotificationVO vo) {
        long epochMs = vo.getCreatedAt() != null
                ? vo.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli() : 0L;
        return new NotificationAdminViewDTO(
                vo.getId() != null ? vo.getId() : "",
                vo.getAnnouncementId(),
                vo.getTitle() != null ? vo.getTitle() : "",
                vo.getType() != null ? vo.getType() : "",
                vo.getCategory() != null ? vo.getCategory() : "",
                epochMs);
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        return switch (e.getErrorCode().code()) {
            case 40400 -> // NOT_FOUND
                    RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            case 40000 -> // BAD_REQUEST
                    RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}

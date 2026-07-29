package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.NotificationAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.notification.service.NotificationAdministrationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo Provider implementation of {@link NotificationAdministrationService} in {@code backend-app}.
 *
 * <p>Delegates to {@link NotificationAdministrationDomainService} for canonical write-side domain logic.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class NotificationAdministrationProvider implements NotificationAdministrationService {

    private final NotificationAdministrationDomainService domainService;

    @Override
    public RpcResult<NotificationAdminViewDTO> createNotification(CreateNotificationCommand command) {
        log.info("NotificationAdministrationProvider.createNotification title={} target={} commandId={} actor={}",
                command.title(), command.target(),
                command.commandId(), command.actor().actorId());
        try {
            NotificationAdminViewDTO dto = domainService.createNotification(command);
            return RpcResult.success(dto, command.trace().traceId());
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
            domainService.deleteNotification(command);
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
            NotificationAdminViewDTO dto = domainService.updateNotification(command);
            return RpcResult.success(dto, command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("NotificationAdministrationProvider.updateNotification unexpected error id={}",
                    command.notificationId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        if (e.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        return switch (e.getErrorCode().code()) {
            case 40400 -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}

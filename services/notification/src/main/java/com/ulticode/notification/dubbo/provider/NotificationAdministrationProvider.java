package com.ulticode.notification.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.WriteCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.notification.idempotency.CommandReceiptExecutor;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.NotificationAdministrationService;
import com.ulticode.app.api.service.NotificationServiceContract;
import com.ulticode.notification.security.AdminActorAuthorizer;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.notification.service.NotificationAdministrationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo Provider implementation of
 * {@link NotificationAdministrationService}. It is hosted by
 * {@code backend-notification} owner group.
 *
 * <p>Delegates to {@link NotificationAdministrationDomainService} for canonical write-side domain logic.
 */
@Slf4j
@DubboService(group = NotificationServiceContract.DUBBO_GROUP,
        version = NotificationServiceContract.DUBBO_VERSION)
@RequiredArgsConstructor
public class NotificationAdministrationProvider implements NotificationAdministrationService {

    private static final String SERVICE = "NotificationAdministrationService";
    private static final String CREATE_OPERATION = "createNotification";
    private static final String DELETE_OPERATION = "deleteNotification";
    private static final String UPDATE_OPERATION = "updateNotification";

    private final NotificationAdministrationDomainService domainService;
    private final CommandReceiptExecutor receiptExecutor;
    private final AdminActorAuthorizer actorAuthorizer;

    @Override
    public RpcResult<NotificationAdminViewDTO> createNotification(CreateNotificationCommand command) {
        RpcResult<NotificationAdminViewDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        rejected = rejectIfCreatorMismatch(command);
        if (rejected != null) {
            return rejected;
        }
        log.info("NotificationAdministrationProvider.createNotification title={} target={} commandId={} actor={}",
                command.title(), command.target(),
                command.commandId(), command.actor().actorId());
        try {
            return receiptExecutor.execute(
                    SERVICE,
                    CREATE_OPERATION,
                    command,
                    NotificationAdminViewDTO.class,
                    traceId -> {
                        try {
                            return RpcResult.success(domainService.createNotification(command), traceId);
                        } catch (BusinessException exception) {
                            return toFailure(exception, traceId);
                        } catch (Exception exception) {
                            log.error("NotificationAdministrationProvider.createNotification unexpected error", exception);
                            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
                        }
                    });
        } catch (Exception exception) {
            log.error("NotificationAdministrationProvider.createNotification receipt failure", exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId(command));
        }
    }

    @Override
    public RpcResult<Void> deleteNotification(DeleteNotificationCommand command) {
        RpcResult<Void> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        log.info("NotificationAdministrationProvider.deleteNotification id={} commandId={} actor={}",
                command.notificationId(),
                command.commandId(), command.actor().actorId());
        try {
            return receiptExecutor.execute(
                    SERVICE,
                    DELETE_OPERATION,
                    command,
                    Void.class,
                    traceId -> {
                        try {
                            domainService.deleteNotification(command);
                            return RpcResult.success(traceId);
                        } catch (BusinessException exception) {
                            return toFailure(exception, traceId);
                        } catch (Exception exception) {
                            log.error("NotificationAdministrationProvider.deleteNotification unexpected error id={}",
                                    command.notificationId(), exception);
                            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
                        }
                    });
        } catch (Exception exception) {
            log.error("NotificationAdministrationProvider.deleteNotification receipt failure id={}",
                    command.notificationId(), exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId(command));
        }
    }

    @Override
    public RpcResult<NotificationAdminViewDTO> updateNotification(UpdateNotificationCommand command) {
        RpcResult<NotificationAdminViewDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        log.info("NotificationAdministrationProvider.updateNotification id={} commandId={} actor={}",
                command.notificationId(),
                command.commandId(), command.actor().actorId());
        try {
            return receiptExecutor.execute(
                    SERVICE,
                    UPDATE_OPERATION,
                    command,
                    NotificationAdminViewDTO.class,
                    traceId -> {
                        try {
                            return RpcResult.success(domainService.updateNotification(command), traceId);
                        } catch (BusinessException exception) {
                            return toFailure(exception, traceId);
                        } catch (Exception exception) {
                            log.error("NotificationAdministrationProvider.updateNotification unexpected error id={}",
                                    command.notificationId(), exception);
                            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
                        }
                    });
        } catch (Exception exception) {
            log.error("NotificationAdministrationProvider.updateNotification receipt failure id={}",
                    command.notificationId(), exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId(command));
        }
    }

    private <T> RpcResult<T> rejectIfNotAdmin(WriteCommand command) {
        if (command == null) {
            return RpcResult.failure(AppErrorCode.BAD_REQUEST, null);
        }
        ActorDelegation actor = command.actor();
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())
                || (!"ADMIN".equalsIgnoreCase(actor.actorType())
                && !"SUPER_ADMIN".equalsIgnoreCase(actor.actorType()))) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        try {
            if (actorAuthorizer == null || !actorAuthorizer.isAuthorized(actor)) {
                return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
            }
        } catch (RuntimeException exception) {
            log.warn("NotificationAdministrationProvider actor authorization failed", exception);
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        return null;
    }

    private RpcResult<NotificationAdminViewDTO> rejectIfCreatorMismatch(CreateNotificationCommand command) {
        if (command.creatorAccountId() == null
                || !command.creatorAccountId().equals(command.actor().actorId())) {
            return RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId(command));
        }
        return null;
    }

    private static String traceId(WriteCommand command) {
        return CommandReceiptExecutor.traceId(command);
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        if (e.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        return switch (e.getErrorCode().code()) {
            case 40000 -> RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId);
            case 40400 -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}

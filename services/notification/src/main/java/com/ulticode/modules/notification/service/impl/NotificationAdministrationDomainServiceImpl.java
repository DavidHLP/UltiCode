package com.ulticode.modules.notification.service.impl;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.modules.notification.port.NotificationAdministrationWritePort;
import com.ulticode.modules.notification.service.NotificationAdministrationDomainService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationAdministrationDomainServiceImpl implements NotificationAdministrationDomainService {

    private final NotificationAdministrationWritePort writePort;

    public NotificationAdministrationDomainServiceImpl(NotificationAdministrationWritePort writePort) {
        this.writePort = writePort;
    }

    @Override
    public NotificationAdminViewDTO createNotification(CreateNotificationCommand command) {
        log.info("NotificationAdministrationDomainServiceImpl.createNotification commandId={} title={}",
                command.commandId(), command.title());
        return writePort.createNotification(command);
    }

    @Override
    public void deleteNotification(DeleteNotificationCommand command) {
        log.info("NotificationAdministrationDomainServiceImpl.deleteNotification commandId={} notificationId={}",
                command.commandId(), command.notificationId());
        writePort.deleteNotification(command);
    }

    @Override
    public NotificationAdminViewDTO updateNotification(UpdateNotificationCommand command) {
        log.info("NotificationAdministrationDomainServiceImpl.updateNotification commandId={} notificationId={}",
                command.commandId(), command.notificationId());
        return writePort.updateNotification(command);
    }
}

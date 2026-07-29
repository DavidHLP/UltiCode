package com.ulticode.modules.notification.service;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;

public interface NotificationAdministrationDomainService {

    NotificationAdminViewDTO createNotification(CreateNotificationCommand command);

    void deleteNotification(DeleteNotificationCommand command);

    NotificationAdminViewDTO updateNotification(UpdateNotificationCommand command);
}

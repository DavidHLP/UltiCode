package com.ulticode.modules.notification.service;

import com.ulticode.notification.api.command.CreateNotificationCommand;
import com.ulticode.notification.api.command.DeleteNotificationCommand;
import com.ulticode.notification.api.command.UpdateNotificationCommand;
import com.ulticode.notification.api.dto.NotificationAdminViewDTO;

public interface NotificationAdministrationDomainService {

    NotificationAdminViewDTO createNotification(CreateNotificationCommand command);

    void deleteNotification(DeleteNotificationCommand command);

    NotificationAdminViewDTO updateNotification(UpdateNotificationCommand command);
}

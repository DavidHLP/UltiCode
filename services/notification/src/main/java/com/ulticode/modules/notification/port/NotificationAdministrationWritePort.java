package com.ulticode.modules.notification.port;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;

/**
 * App-owned write port interface for notification administration.
 */
public interface NotificationAdministrationWritePort {

    NotificationAdminViewDTO createNotification(CreateNotificationCommand command);

    void deleteNotification(DeleteNotificationCommand command);

    NotificationAdminViewDTO updateNotification(UpdateNotificationCommand command);
}

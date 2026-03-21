package com.ulticode.modules.notification.dto;

import lombok.Data;

/**
 * DTO for updating a notification (mainly marking as read).
 */
@Data
public class UpdateNotificationDTO {
    private Boolean isRead;
}

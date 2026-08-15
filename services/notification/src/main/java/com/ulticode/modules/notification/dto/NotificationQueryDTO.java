package com.ulticode.modules.notification.dto;

import lombok.Data;

/**
 * Query parameters for listing notifications.
 */
@Data
public class NotificationQueryDTO {
    private String type;
    private String category;
    private Boolean isRead;
    private Integer page = 1;
    private Integer limit = 20;
}

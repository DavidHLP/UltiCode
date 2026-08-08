package com.ulticode.modules.notification.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * View object for notification response.
 */
@Data
public class NotificationVO {
    private String id;
    private String type;
    private String category;
    private String title;
    private String body;
    private String link;
    private Map<String, Object> metadata;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}

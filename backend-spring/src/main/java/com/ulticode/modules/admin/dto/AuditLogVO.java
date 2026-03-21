package com.ulticode.modules.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AuditLogVO {
    private String id;
    private String performerId;
    private String performerName;
    private String performerUsername;
    private String userId;
    private String userName;
    private String userUsername;
    private String action;
    private String entityType;
    private String entityId;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
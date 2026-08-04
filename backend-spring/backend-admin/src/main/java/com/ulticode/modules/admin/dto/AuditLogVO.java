package com.ulticode.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Audit log view object with nested performer and user information.
 */
@Data
public class AuditLogVO {
    private String id;
    private PerformerInfo performer;
    private UserInfo user;
    private String action;
    private String entityType;
    private String entityId;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;

    /**
     * Performer information - who performed the action.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformerInfo {
        private String id;
        private String username;
        private String name;
        private String role;
    }

    /**
     * Target user information - who the action was performed on.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String username;
        private String name;
    }
}

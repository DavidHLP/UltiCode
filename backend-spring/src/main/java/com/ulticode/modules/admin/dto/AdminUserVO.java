package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin user view object with full information.
 * Follows Java naming conventions with camelCase field names.
 * Jackson serialization will use camelCase by default.
 */
@Data
@Schema(description = "Admin user details")
public class AdminUserVO {

    @Schema(description = "User ID")
    private String id;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Email address")
    private String email;

    @Schema(description = "Avatar URL")
    private String avatar;

    @Schema(description = "User role")
    private String role;

    @Schema(description = "Is user active")
    private Boolean isActive;

    @Schema(description = "Is user banned")
    private Boolean isBanned;

    @Schema(description = "Ban reason")
    private String banReason;

    @Schema(description = "Ban expiration time")
    private LocalDateTime bannedUntil;

    @Schema(description = "When user joined")
    private LocalDateTime joinedAt;

    @Schema(description = "Last login time")
    private LocalDateTime lastLoginAt;

    @Schema(description = "User permissions")
    private List<PermissionInfo> permissions;

    @Schema(description = "User statistics")
    private UserStatsInfo stats;

    @Data
    @Schema(description = "Permission information")
    public static class PermissionInfo {
        private String action;
        private String resource;
        private String source; // 'role' or 'direct'
        private LocalDateTime expiresAt;
    }

    @Data
    @Schema(description = "User statistics")
    public static class UserStatsInfo {
        private Integer totalSubmissions;
        private Integer acceptedSubmissions;
        private Integer totalSolutions;
        private Integer streak;
    }
}

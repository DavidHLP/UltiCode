package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin user view object with full information
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
    @JsonAlias("isActive")
    private Boolean is_active;

    @Schema(description = "Is user banned")
    @JsonAlias("isBanned")
    private Boolean is_banned;

    @Schema(description = "Ban reason")
    @JsonAlias("banReason")
    private String ban_reason;

    @Schema(description = "When user was banned")
    @JsonAlias("bannedAt")
    private LocalDateTime banned_at;

    @Schema(description = "When user joined")
    @JsonAlias("joinedAt")
    private LocalDateTime joined_at;

    @Schema(description = "Last login time")
    @JsonAlias("lastLoginAt")
    private LocalDateTime last_login_at;

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
        private LocalDateTime expires_at;
    }

    @Data
    @Schema(description = "User statistics")
    public static class UserStatsInfo {
        private Integer total_submissions;
        private Integer accepted_submissions;
        private Integer total_solutions;
        private Integer streak;
    }
}

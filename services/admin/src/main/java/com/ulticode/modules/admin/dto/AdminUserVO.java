package com.ulticode.modules.admin.dto;

import com.ulticode.common.response.DegradationStatus;
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

    /**
     * Explicit degradation marker for cross-owner enrichment of this detail
     * view. {@code null} (treated as healthy) when every source answered;
     * {@code PARTIAL} when an optional enrichment source (for example the
     * App-owned profile) was unavailable.
     */
    @Schema(description = "Degradation marker when cross-owner enrichment was partial")
    private DegradationStatus degradationStatus;
    /**
     * Stable top-level status for the detail read. List responses leave the
     * existing nullable marker unchanged.
     */
    @Schema(description = "Top-level detail availability")
    private DegradationStatus detailStatus;

    @Schema(description = "Profile section availability")
    private DegradationStatus profileStatus;

    @Schema(description = "Profile section degradation reason")
    private String profileReason;

    @Schema(description = "Statistics section availability")
    private DegradationStatus statsStatus;

    @Schema(description = "Statistics section degradation reason")
    private String statsReason;

    @Schema(description = "Permissions section availability")
    private DegradationStatus permissionsStatus;

    @Schema(description = "Permissions section degradation reason")
    private String permissionsReason;

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

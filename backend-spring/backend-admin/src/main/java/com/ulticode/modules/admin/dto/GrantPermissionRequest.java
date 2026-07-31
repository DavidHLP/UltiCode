package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 授予用户直接权限的请求 DTO。
 *
 * <p>对应 POST /admin/users/{id}/permissions。
 * action / resource 必须是 user_permissions 表 ENUM 列允许的值。
 */
@Data
@Schema(description = "Request to grant a direct permission to a user")
public class GrantPermissionRequest {

    @NotBlank(message = "action must not be blank")
    @Size(max = 32)
    @Schema(description = "Action enum, e.g. MANAGE_PERMISSIONS/MANAGE_USERS/MODERATE/CREATE/READ/UPDATE/DELETE/PUBLISH",
            example = "MANAGE_PERMISSIONS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @NotBlank(message = "resource must not be blank")
    @Size(max = 64)
    @Schema(description = "Resource enum, e.g. SYSTEM/USER/PROBLEM/CONTEST/SOLUTION/FORUM_POST/FORUM_COMMENT/PROBLEM_LIST/TAG",
            example = "SYSTEM", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resource;

    @Schema(description = "Expiration timestamp (ISO-8601). null means permanent grant.",
            example = "2026-12-31T23:59:59")
    private LocalDateTime expiresAt;
}

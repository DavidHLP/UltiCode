package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置用户密码请求 DTO。
 *
 * <p><b>命名差异提示</b>: 本模块 (admin) 使用字段名 <code>password</code>,
 * 而 <code>com.ulticode.modules.auth.dto.ResetPasswordDTO</code> (公开 token-based 重置)
 * 使用 <code>newPassword</code>。前端 management 已与本端点对齐使用 <code>password</code>。
 * 详见 <code>docs/api-field-naming-conventions.md</code>。
 */
@Data
@Schema(description = "Request to reset a user's password (admin operation)")
public class ResetPasswordRequest {

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Schema(description = "New password (8-128 chars). NOTE: admin module uses field name " +
                          "'password' while auth/dto/ResetPasswordDTO uses 'newPassword'. " +
                          "Frontend (management) uses 'password' to align with this admin endpoint.",
            example = "S3cur3P@ssw0rd",
            minLength = 8, maxLength = 128,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}

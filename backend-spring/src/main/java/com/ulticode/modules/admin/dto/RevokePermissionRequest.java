package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 撤销用户直接权限的请求 DTO。
 *
 * <p>对应 DELETE /admin/users/{id}/permissions (Spring 支持带 body 的 DELETE)。
 * 撤销不存在的权限会返回 200,符合 REST DELETE 幂等语义。
 */
@Data
@Schema(description = "Request to revoke a direct permission from a user")
public class RevokePermissionRequest {

    @NotBlank(message = "action must not be blank")
    @Size(max = 32)
    @Schema(description = "Action enum to revoke",
            example = "MANAGE_PERMISSIONS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @NotBlank(message = "resource must not be blank")
    @Size(max = 64)
    @Schema(description = "Resource enum to revoke",
            example = "SYSTEM", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resource;
}

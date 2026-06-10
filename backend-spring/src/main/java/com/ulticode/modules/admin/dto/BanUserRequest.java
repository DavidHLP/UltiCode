package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员封禁用户请求 DTO。
 *
 * <p><b>注意</b>: 本 DTO 不存在 <code>duration</code> 字段; 时长用 <code>until</code> (ISO-8601)
 * 表达,前端如需"封禁 N 天"应在客户端计算 <code>now + N days</code> 后传入。
 */
@Data
@Schema(description = "Request to ban a user")
public class BanUserRequest {

    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    @Schema(description = "Human-readable ban reason (visible to support/audit)",
            example = "Violation of terms - repeated spam in forum")
    private String reason;

    @Schema(description = "Ban end timestamp (ISO-8601 local datetime, no offset). " +
                          "null means permanent ban. There is NO 'duration' field — " +
                          "compute the end time on the client side.",
            example = "2026-12-31T23:59:59",
            format = "date-time")
    private String until;
}

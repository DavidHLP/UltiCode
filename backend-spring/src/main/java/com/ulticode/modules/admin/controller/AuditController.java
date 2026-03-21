package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.modules.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Audit", description = "审计日志管理接口")
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "获取审计日志列表")
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AuditLogVO>> getAuditLogs(AuditLogQueryDTO query) {
        return Result.success(auditService.getAuditLogs(query));
    }

    @Operation(summary = "获取审计统计")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AuditStatsVO> getAuditStats(AuditLogQueryDTO query) {
        return Result.success(auditService.getAuditStats(query));
    }
}
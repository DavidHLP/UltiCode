package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.modules.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "Admin - Audit", description = "审计日志管理接口")
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AuditController {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

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

    @Operation(summary = "导出审计日志")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void exportAuditLogs(AuditLogQueryDTO query,
                                @RequestParam(defaultValue = "csv") String format,
                                HttpServletResponse response) throws IOException {
        if (!"csv".equalsIgnoreCase(format) && !"json".equalsIgnoreCase(format)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            String traceId = TraceIdUtil.current();
            objectMapper.writeValue(response.getWriter(),
                Result.error(AdminErrorCode.BAD_REQUEST.getCode(),
                    "Unsupported format: " + format, traceId));
            return;
        }
        List<AuditLogVO> logs = auditService.getAuditLogsForExport(query);

        if ("json".equalsIgnoreCase(format)) {
            response.setContentType("application/json");
            response.setHeader("Content-Disposition",
                "attachment; filename=audit-logs.json");
            objectMapper.writeValue(response.getOutputStream(), logs);
        } else {
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition",
                "attachment; filename=audit-logs.csv");
            PrintWriter writer = response.getWriter();
            writer.println("id,action,entityType,entityId,performer,ipAddress,createdAt");
            for (AuditLogVO log : logs) {
                writer.println(String.join(",",
                    escapeCsvField(log.getId()),
                    escapeCsvField(log.getAction()),
                    escapeCsvField(log.getEntityType()),
                    escapeCsvField(log.getEntityId()),
                    escapeCsvField(log.getPerformer() != null ? log.getPerformer().getUsername() : ""),
                    escapeCsvField(log.getIpAddress() != null ? log.getIpAddress() : ""),
                    escapeCsvField(log.getCreatedAt() != null
                        ? log.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : "")
                ));
            }
            writer.flush();
        }
    }

    private static String escapeCsvField(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}

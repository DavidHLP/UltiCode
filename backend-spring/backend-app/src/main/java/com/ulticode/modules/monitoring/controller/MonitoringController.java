package com.ulticode.modules.monitoring.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.monitoring.dto.DatabaseStatsVO;
import com.ulticode.modules.monitoring.dto.QueueStatsVO;
import com.ulticode.modules.monitoring.dto.RedisStatsVO;
import com.ulticode.modules.monitoring.dto.ResourceUsageVO;
import com.ulticode.modules.monitoring.dto.SystemHealthVO;
import com.ulticode.modules.monitoring.dto.SystemInfoVO;
import com.ulticode.modules.monitoring.inspector.MonitoringInspector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for system monitoring endpoints.
 *
 * <p>Depends on {@link MonitoringInspector} for every read; no
 * monitoring write path exists in the codebase, so no
 * {@code MonitoringService} collaborator is needed. The
 * {@link PreAuthorize} annotations stay on the controller — the
 * inspector itself is security-agnostic and reusable from any
 * non-HTTP caller (scheduled job, Arthas probe, future Prometheus
 * exporter).
 *
 * <p>All endpoints require admin privileges.
 */
@Tag(name = "Admin - Monitoring", description = "系统监控接口")
@RestController
@Profile("!test")
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class MonitoringController {

    private final MonitoringInspector monitoringInspector;

    @Operation(summary = "获取系统信息")
    @GetMapping("/system")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<SystemInfoVO> getSystemInfo() {
        return Result.success(monitoringInspector.getSystemInfo());
    }

    @Operation(summary = "获取资源使用情况")
    @GetMapping("/resources")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ResourceUsageVO> getResourceUsage() {
        return Result.success(monitoringInspector.getResourceUsage());
    }

    @Operation(summary = "获取数据库统计")
    @GetMapping("/database")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<DatabaseStatsVO> getDatabaseStats() {
        return Result.success(monitoringInspector.getDatabaseStats());
    }

    @Operation(summary = "获取队列统计")
    @GetMapping("/queues")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<QueueStatsVO>> getQueueStats() {
        return Result.success(monitoringInspector.getQueueStats());
    }

    @Operation(summary = "获取Redis统计")
    @GetMapping("/redis")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<RedisStatsVO> getRedisStats() {
        return Result.success(monitoringInspector.getRedisStats());
    }

    @Operation(summary = "系统健康检查")
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<SystemHealthVO> getHealthCheck() {
        return Result.success(monitoringInspector.getHealthCheck());
    }
}

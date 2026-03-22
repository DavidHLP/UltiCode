package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard controller for admin statistics.
 */
@Tag(name = "Admin Dashboard", description = "Admin dashboard statistics endpoints")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get dashboard stats", description = "Get comprehensive dashboard statistics")
    @GetMapping("/stats")
    public Result<DashboardStatsVO> getStats() {
        DashboardStatsVO stats = dashboardService.getStats();
        return Result.success(stats);
    }

    @Operation(summary = "Get chart stats", description = "Get statistics for charts")
    @GetMapping("/charts")
    public Result<ChartStatsVO> getChartStats(
            @RequestParam(defaultValue = "users") String metric,
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) Integer days) {
        ChartStatsVO stats = dashboardService.getChartStats(metric, period, days);
        return Result.success(stats);
    }
}

package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard controller for admin statistics.
 */
@Tag(name = "Admin Dashboard", description = "Admin dashboard statistics endpoints")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
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
            @RequestParam(defaultValue = "users")
            @Pattern(regexp = "users|submissions|contests|problems|solutions|forum_posts",
                    message = "Invalid metric. Allowed: users, submissions, contests, problems, solutions, forum_posts")
            String metric,
            @RequestParam(defaultValue = "day")
            @Pattern(regexp = "hour|day|week|month|year",
                    message = "Invalid period. Allowed: hour, day, week, month, year")
            String period,
            @RequestParam(required = false)
            @Min(value = 1, message = "Days parameter must be at least 1")
            @Max(value = 365, message = "Days parameter cannot exceed 365")
            Integer days) {
        ChartStatsVO stats = dashboardService.getChartStats(metric, period, days);
        return Result.success(stats);
    }
}

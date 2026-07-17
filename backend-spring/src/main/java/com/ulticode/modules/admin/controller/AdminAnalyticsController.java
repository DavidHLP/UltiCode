package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for analytics and reporting.
 * Provides aggregated data for various analytics reports.
 */
@Tag(name = "Admin - Analytics", description = "Analytics and reporting endpoints for admin panel")
@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @Operation(summary = "Get analytics overview", description = "Get lightweight aggregated summary across all analytics dimensions. "
            + "Returns totalUsers, activeUsers, totalSubmissions, acceptedSubmissions, "
            + "acceptanceRate, totalContests, activeSubscriptions, systemUptimeSeconds, memoryUsagePercent, periodDays.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AnalyticsOverviewVO> getAnalyticsOverview(
            @Parameter(description = "Number of days to analyze")
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminAnalyticsService.getAnalyticsOverview(days));
    }

    @Operation(summary = "Get user activity report", description = "Get user activity analytics with DAU, retention, and active hours")
    @GetMapping("/user-activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<UserActivityReportVO> getUserActivity(
            @Parameter(description = "Number of days to analyze")
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminAnalyticsService.getUserActivityReport(days));
    }

    @Operation(summary = "Get problem completion report", description = "Get problem completion statistics by difficulty and tags")
    @GetMapping("/problem-completion")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemCompletionReportVO> getProblemCompletion(
            @Parameter(description = "Number of days to analyze")
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminAnalyticsService.getProblemCompletionReport(days));
    }

    @Operation(summary = "Get contest participation report", description = "Get contest participation analytics and trends")
    @GetMapping("/contest-participation")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ContestParticipationReportVO> getContestParticipation(
            @Parameter(description = "Number of days to analyze")
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminAnalyticsService.getContestParticipationReport(days));
    }

    @Operation(summary = "Get revenue report", description = "Get revenue and subscription analytics")
    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<RevenueReportVO> getRevenue(
            @Parameter(description = "Number of days to analyze")
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(adminAnalyticsService.getRevenueReport(days));
    }

    @Operation(summary = "Get performance report", description = "Get system performance metrics and resource usage")
    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PerformanceReportVO> getPerformance() {
        return Result.success(adminAnalyticsService.getPerformanceReport());
    }
}

package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.projection.DashboardStatsProjection;
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

  /**
   * Whitelisted metric values for chart stats. Order is stable for OpenAPI docs.
   * NOTE: {@link #METRICS_REGEX} and {@link #METRICS_HUMAN} must be kept in sync when this list
   * changes — Java annotation arguments require compile-time constants, so they cannot be
   * derived from the array at compile time.
   */
  static final String[] ALLOWED_METRICS =
      {"users", "submissions", "contests", "problems", "solutions", "forum_posts"};

  /** Whitelisted period values for chart stats. Same sync rule as {@link #ALLOWED_METRICS}. */
  static final String[] ALLOWED_PERIODS = {"hour", "day", "week", "month", "year"};

  private static final String METRICS_REGEX =
      "users|submissions|contests|problems|solutions|forum_posts";
  private static final String METRICS_HUMAN =
      "users, submissions, contests, problems, solutions, forum_posts";
  private static final String PERIODS_REGEX = "hour|day|week|month|year";
  private static final String PERIODS_HUMAN = "hour, day, week, month, year";

  private final DashboardStatsProjection dashboardStatsProjection;

  @Operation(summary = "Get dashboard stats", description = "Get comprehensive dashboard statistics")
  @GetMapping("/stats")
  public Result<DashboardStatsVO> getStats() {
    DashboardStatsVO stats = dashboardStatsProjection.loadStats();
    return Result.success(stats);
  }

  @Operation(summary = "Get chart stats", description = "Get statistics for charts")
  @GetMapping("/charts")
  public Result<ChartStatsVO> getChartStats(
      @RequestParam(defaultValue = "users")
      @Pattern(regexp = METRICS_REGEX, message = "Invalid metric. Allowed: " + METRICS_HUMAN)
      String metric,
      @RequestParam(defaultValue = "day")
      @Pattern(regexp = PERIODS_REGEX, message = "Invalid period. Allowed: " + PERIODS_HUMAN)
      String period,
      @RequestParam(required = false)
      @Min(value = 1, message = "Days parameter must be at least 1")
      @Max(value = 365, message = "Days parameter cannot exceed 365")
      Integer days) {
    ChartStatsVO stats = dashboardStatsProjection.loadChartStats(metric, period, days);
    return Result.success(stats);
  }
}

package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Analytics Overview View Object.
 * Lightweight aggregated summary across all analytics dimensions.
 *
 * <p>Field declaration order is significant: it defines the serialized JSON
 * key order, which must remain stable for the documented
 * {@code GET /admin/analytics} contract. This VO replaces the historical
 * {@code Map<String, Object>} response with a typed shape while preserving the
 * exact field names and value types.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyticsOverviewVO {

    /** Total registered users. */
    private Long totalUsers;

    /** Distinct users with submissions in the period. */
    private Long activeUsers;

    /** Total submissions in the period. */
    private Long totalSubmissions;

    /** Accepted submissions in the period. */
    private Long acceptedSubmissions;

    /** Acceptance rate percentage (0–100, 2 decimals). */
    private Double acceptanceRate;

    /** Contests started in the period. */
    private Long totalContests;

    /** Subscriptions with status ACTIVE. */
    private Long activeSubscriptions;

    /** JVM uptime in seconds. */
    private Long systemUptimeSeconds;

    /** JVM heap usage percentage (0–100, 2 decimals). */
    private Double memoryUsagePercent;

    /** Echo of the resolved {@code days} parameter. */
    private Integer periodDays;
}

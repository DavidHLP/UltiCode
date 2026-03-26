package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Revenue Report View Object.
 * Contains subscription and revenue statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RevenueReportVO {

    /**
     * Revenue trend data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueTrend {
        private String date;
        private Double revenue;
        private Integer newSubscribers;
        private Integer churned;
    }

    /**
     * Revenue by subscription plan.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanRevenue {
        private String plan;
        private Integer subscribers;
        private Double revenue;
    }

    /**
     * Total revenue (in specified period).
     */
    private Double totalRevenue;

    /**
     * Monthly Recurring Revenue.
     */
    private Double mrr;

    /**
     * Annual Recurring Revenue.
     */
    private Double arr;

    /**
     * Average Revenue Per User.
     */
    private Double arpu;

    /**
     * Total active subscribers.
     */
    private Integer subscriberCount;

    /**
     * Customer churn rate (%).
     */
    private Double churnRate;

    /**
     * Revenue trend over time.
     */
    private List<RevenueTrend> revenueTrend;

    /**
     * Revenue breakdown by subscription plan.
     */
    private List<PlanRevenue> byPlan;

    /**
     * Conversion rate (%).
     */
    private Double conversionRate;
}

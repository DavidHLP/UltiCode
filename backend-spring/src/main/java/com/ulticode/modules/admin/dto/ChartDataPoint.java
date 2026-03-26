package com.ulticode.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single data point for chart statistics.
 * Represents a time-series data point with date and count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataPoint {

    /**
     * The date/time bucket for this data point.
     * Format depends on the period (e.g., "2026-03-25" for daily).
     */
    private String date;

    /**
     * The count/value for this data point.
     */
    private Long count;
}

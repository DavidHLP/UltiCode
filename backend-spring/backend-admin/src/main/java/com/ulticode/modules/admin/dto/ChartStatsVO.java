package com.ulticode.modules.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chart statistics response.
 */
@Data
public class ChartStatsVO {

    private String metric;
    private String period;
    private List<ChartDataPoint> data;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}

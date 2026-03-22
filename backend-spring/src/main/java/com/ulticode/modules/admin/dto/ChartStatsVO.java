package com.ulticode.modules.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Chart statistics response.
 */
@Data
public class ChartStatsVO {

    private String metric;
    private String period;
    private List<Map<String, Object>> data;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}

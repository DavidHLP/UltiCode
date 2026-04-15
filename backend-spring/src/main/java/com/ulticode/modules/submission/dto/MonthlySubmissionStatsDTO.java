package com.ulticode.modules.submission.dto;

import lombok.Data;

/**
 * DTO for monthly submission statistics.
 */
@Data
public class MonthlySubmissionStatsDTO {
    private String month;
    private Integer totalCount;
    private Integer acceptedCount;
}

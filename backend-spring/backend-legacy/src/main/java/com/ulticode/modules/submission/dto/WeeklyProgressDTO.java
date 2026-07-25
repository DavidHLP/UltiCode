package com.ulticode.modules.submission.dto;

import lombok.Data;

/**
 * DTO for weekly progress statistics.
 */
@Data
public class WeeklyProgressDTO {
    private String weekRange;
    private Integer solvedCount;
    private Double timeSpentHours;
}

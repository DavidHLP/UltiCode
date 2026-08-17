package com.ulticode.submission.api.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * DTO for weekly progress statistics.
 */
@Data
public class WeeklyProgressDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String weekRange;
    private Integer solvedCount;
    private Double timeSpentHours;
}

package com.ulticode.app.api.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * DTO for weekly progress statistics.
 */
@Data
public class WeeklyProgressDTO implements Serializable {
    private String weekRange;
    private Integer solvedCount;
    private Double timeSpentHours;
}

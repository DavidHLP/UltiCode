package com.ulticode.app.api.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * DTO for monthly submission statistics.
 */
@Data
public class MonthlySubmissionStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String month;
    private Integer totalCount;
    private Integer acceptedCount;
}

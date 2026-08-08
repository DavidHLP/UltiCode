package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Weekly active-user aggregation row: the ISO week-start date, the MySQL
 * {@code YEARWEEK} key, and the distinct submitter count for that week.
 * Backs {@code countWeeklyActiveUsers}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyActiveUserCount implements Serializable {
    private String weekStart;
    private Integer yearWeek;
    private Long count;
}

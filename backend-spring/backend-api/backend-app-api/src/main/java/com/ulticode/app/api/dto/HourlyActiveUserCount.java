package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Peak-hour aggregation row: the hour of day (0-23) and the distinct
 * submitter count in that hour. Backs {@code countActiveUsersByHour}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HourlyActiveUserCount {
    private Integer hour;
    private Long count;
}

package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Daily active-user aggregation row: the submission date and the distinct
 * submitter count for that day. Backs {@code countDailyActiveUsers}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyActiveUserCount implements Serializable {
    private String date;
    private Long count;
}

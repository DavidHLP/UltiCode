package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Activity Report View Object.
 * Contains daily active users, retention rates, and peak activity hours.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserActivityReportVO {

    /**
     * Daily active users data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyActiveUsers {
        private String date;
        private Integer count;
    }

    /**
     * User retention rates.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRetention {
        private Double day1;   // Next day retention rate (%)
        private Double day7;   // 7-day retention rate (%)
        private Double day30;  // 30-day retention rate (%)
    }

    /**
     * Peak active hour data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeakActiveHour {
        private Integer hour;  // Hour of day (0-23)
        private Integer count; // Activity count
    }

    /**
     * Top active user data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopActiveUser {
        private String userId;
        private String username;
        private Integer loginCount;
        private LocalDateTime lastActive;
    }

    /**
     * Daily active users trend.
     */
    private List<DailyActiveUsers> activeUsersDaily;

    /**
     * Weekly active users trend.
     */
    private List<DailyActiveUsers> activeUsersWeekly;

    /**
     * Average session duration in seconds.
     */
    private Double averageSessionDuration;

    /**
     * Peak active hours (hourly breakdown).
     */
    private List<PeakActiveHour> peakActiveHours;

    /**
     * User retention rates.
     */
    private UserRetention userRetention;

    /**
     * Top active users.
     */
    private List<TopActiveUser> topActiveUsers;
}

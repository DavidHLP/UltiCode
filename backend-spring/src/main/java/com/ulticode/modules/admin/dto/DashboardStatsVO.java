package com.ulticode.modules.admin.dto;

import lombok.Data;

import java.util.Map;

/**
 * Dashboard statistics response.
 */
@Data
public class DashboardStatsVO {

    private UserStats users;
    private ProblemStats problems;
    private ContestStats contests;
    private SubmissionStats submissions;
    private SolutionStats solutions;
    private ForumStats forum;
    private SystemStats system;

    @Data
    public static class UserStats {
        private Long total;
        private Long active;
        private Long activeToday;
        private Long activeWeek;
        private Long activeMonth;
        private Long banned;
        private Map<String, Long> byRole;
    }

    @Data
    public static class ProblemStats {
        private Long total;
        private Long published;
        private Long unpublished;
        private Map<String, Long> byDifficulty;
        private Map<String, Long> byStatus;
    }

    @Data
    public static class ContestStats {
        private Long total;
        private Long upcoming;
        private Long running;
        private Long finished;
    }

    @Data
    public static class SubmissionStats {
        private Long total;
        private Long today;
        private Long week;
        private Long month;
        private Double acceptanceRate;
    }

    @Data
    public static class SolutionStats {
        private Long total;
        private Long published;
        private Long flagged;
    }

    @Data
    public static class ForumStats {
        private Long posts;
        private Long comments;
        private Long communities;
        private Long flaggedPosts;
        private Long flaggedComments;
    }

    @Data
    public static class SystemStats {
        private Long uptime;
        private String version;
    }
}

package com.ulticode.modules.moderation.dto;

import lombok.Data;

/**
 * View object for moderation statistics.
 */
@Data
public class ModerationStatsVO {

    /**
     * Number of pending queue items
     */
    private long pendingCount;

    /**
     * Number of items under review
     */
    private long underReviewCount;

    /**
     * Number of items resolved today
     */
    private long resolvedToday;

    /**
     * Average resolution time in minutes
     */
    private long avgResolutionTimeMinutes;

    /**
     * Number of pending appeals
     */
    private long pendingAppealsCount;
}

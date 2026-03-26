package com.ulticode.modules.moderation.dto;

import lombok.Data;

import java.util.Map;

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
     * Number of items resolved
     */
    private long resolvedCount;

    /**
     * Number of items dismissed
     */
    private long dismissedCount;

    /**
     * Number of items resolved today
     */
    private long resolvedToday;

    /**
     * Average resolution time in hours
     */
    private Double avgResolutionTimeHours;

    /**
     * Number of pending appeals
     */
    private long pendingAppealsCount;

    /**
     * Count by category
     */
    private Map<String, Long> byCategory;

    /**
     * Count by entity type
     */
    private Map<String, Long> byEntityType;
}

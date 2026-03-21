package com.ulticode.modules.moderation.dto;

import lombok.Data;

/**
 * DTO for querying moderation queue items.
 */
@Data
public class QueryModerationQueueDTO {

    /**
     * Filter by status
     */
    private String status;

    /**
     * Filter by entity type
     */
    private String entityType;

    /**
     * Filter by assigned moderator
     */
    private String assignedTo;

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Number of items per page
     */
    private Integer limit = 20;
}

package com.ulticode.modules.moderation.dto;

import lombok.Data;

/**
 * DTO for querying appeals.
 */
@Data
public class QueryAppealsDTO {

    /**
     * Filter by status
     */
    private String status;

    /**
     * Filter by queue ID
     */
    private String queueId;

    /**
     * Filter by appellant ID
     */
    private String appellantId;

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Number of items per page
     */
    private Integer limit = 20;
}

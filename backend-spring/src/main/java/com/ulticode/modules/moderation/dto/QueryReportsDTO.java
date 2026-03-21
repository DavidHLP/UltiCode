package com.ulticode.modules.moderation.dto;

import lombok.Data;

/**
 * DTO for querying reports.
 */
@Data
public class QueryReportsDTO {

    /**
     * Filter by status
     */
    private String status;

    /**
     * Filter by category
     */
    private String category;

    /**
     * Filter by reporter ID
     */
    private String reporterId;

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Number of items per page
     */
    private Integer limit = 20;
}

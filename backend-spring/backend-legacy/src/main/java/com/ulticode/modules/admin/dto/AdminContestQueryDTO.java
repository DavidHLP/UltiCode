package com.ulticode.modules.admin.dto;

import lombok.Data;

/**
 * Query parameters for admin contest list.
 */
@Data
public class AdminContestQueryDTO {

    /**
     * Search by title or slug
     */
    private String search;

    /**
     * Filter by contest type: PUBLIC, PRIVATE, VIRTUAL
     */
    private String type;

    /**
     * Filter by status: UPCOMING, RUNNING, FINISHED
     */
    private String status;

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Number of items per page
     */
    private Integer limit = 10;

    /**
     * Sort field
     */
    private String sortBy;

    /**
     * Sort direction: asc or desc
     */
    private String sortOrder;
}

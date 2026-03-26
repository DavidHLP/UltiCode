package com.ulticode.modules.admin.dto;

import lombok.Data;

/**
 * Query parameters for admin comment list.
 */
@Data
public class AdminCommentQueryDTO {

    /**
     * Search in comment content
     */
    private String search;

    /**
     * Filter by comment type: "forum" or "solution"
     */
    private String type;

    /**
     * Filter by flagged status
     */
    private Boolean isFlagged;

    /**
     * Filter by deleted status
     */
    private Boolean isDeleted;

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Page size
     */
    private Integer limit = 10;

    /**
     * Sort by field
     */
    private String sortBy = "createdAt";

    /**
     * Sort order: "asc" or "desc"
     */
    private String sortOrder = "desc";
}

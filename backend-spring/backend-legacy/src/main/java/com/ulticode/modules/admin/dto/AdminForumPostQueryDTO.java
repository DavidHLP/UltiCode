package com.ulticode.modules.admin.dto;

import lombok.Data;

/**
 * Query parameters for admin forum post list.
 */
@Data
public class AdminForumPostQueryDTO {

    /**
     * Search keyword (searches in title, excerpt)
     */
    private String search;

    /**
     * Filter by community ID
     */
    private String communityId;

    /**
     * Filter by author ID
     */
    private String authorId;

    /**
     * Filter by flagged status
     */
    private Boolean isFlagged;

    /**
     * Filter by pinned status
     */
    private Boolean isPinned;

    /**
     * Filter by locked status
     */
    private Boolean isLocked;

    /**
     * Filter by deleted status
     */
    private Boolean isDeleted;

    /**
     * Page number (1-based)
     */
    private Integer page = 1;

    /**
     * Number of items per page
     */
    private Integer limit = 10;

    /**
     * Sort by field (createdAt, updatedAt, viewCount, commentCount)
     */
    private String sortBy = "createdAt";

    /**
     * Sort order (asc, desc)
     */
    private String sortOrder = "desc";
}

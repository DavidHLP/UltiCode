package com.ulticode.modules.solution.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Solution Comment View Object for API responses.
 * Used for the solution comments endpoint.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SolutionCommentVO {

    /**
     * Comment unique identifier
     */
    private String id;

    /**
     * ID of the solution this comment belongs to
     */
    private String solutionId;

    /**
     * ID of the parent comment (for nested replies)
     */
    private String parentId;

    /**
     * User ID who created this comment
     */
    private String userId;

    /**
     * Author ID (same as userId, for frontend compatibility)
     */
    private String authorId;

    /**
     * Author username (populated from user service)
     */
    private String authorUsername;

    /**
     * Author avatar URL (populated from user service)
     */
    private String authorAvatar;

    /**
     * Comment content
     */
    private String content;

    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Whether the comment is flagged for review
     */
    private Boolean isFlagged;
}
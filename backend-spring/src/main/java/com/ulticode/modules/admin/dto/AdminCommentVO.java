package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin Comment View Object for admin panel API responses.
 * Unified VO supporting both forum and solution comment types.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminCommentVO {

    /**
     * Comment unique identifier
     */
    private String id;

    /**
     * Comment content
     */
    private String content;

    /**
     * When the comment was created
     */
    private LocalDateTime createdAt;

    /**
     * When the comment was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * ID of the user who created this comment
     */
    private String authorId;

    /**
     * ID of the parent comment (for nested replies)
     */
    private String parentCommentId;

    /**
     * Comment type: "forum" or "solution"
     */
    private String type;

    /**
     * Unified parent ID (post_id for forum, solution_id for solution)
     */
    private String parentId;

    /**
     * Parent title (post title or solution title)
     */
    private String parentTitle;

    /**
     * Author username
     */
    private String username;

    /**
     * Author avatar URL
     */
    private String avatar;

    /**
     * Whether the comment is flagged for review
     */
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    private String flaggedReason;

    /**
     * When the comment was flagged
     */
    private LocalDateTime flaggedAt;

    /**
     * Whether the comment is soft deleted
     */
    private Boolean isDeleted;

    /**
     * When the comment was deleted
     */
    private LocalDateTime deletedAt;

    /**
     * User ID who deleted the comment
     */
    private String deletedBy;
}

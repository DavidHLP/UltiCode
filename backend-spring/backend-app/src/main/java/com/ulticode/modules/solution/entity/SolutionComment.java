package com.ulticode.modules.solution.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Solution comment entity representing the solution_comments table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("solution_comments")
public class SolutionComment {

    /**
     * Comment unique identifier (UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * ID of the solution this comment belongs to
     */
    @TableField("solution_id")
    private String solutionId;

    /**
     * ID of the parent comment (for nested replies)
     */
    @TableField("parent_id")
    private String parentId;

    /**
     * ID of the user who created this comment
     */
    @TableField("user_id")
    private String userId;

    /**
     * Comment content
     */
    private String content;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Whether the comment is flagged for review
     */
    @TableField("is_flagged")
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    @TableField("flagged_reason")
    private String flaggedReason;

    /**
     * When the comment was flagged
     */
    @TableField("flagged_at")
    private LocalDateTime flaggedAt;

    /**
     * Soft delete flag
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    /**
     * When the comment was deleted
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /**
     * User ID who deleted the comment
     */
    @TableField("deleted_by")
    private String deletedBy;
}

package com.ulticode.modules.solution.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Solution entity representing the solutions table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("solutions")
public class Solution {

    /**
     * Solution unique identifier (UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * Problem ID this solution belongs to
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * User ID who created this solution
     */
    @TableField("user_id")
    private String userId;

    /**
     * Solution title
     */
    private String title;

    /**
     * Solution content (markdown)
     */
    private String content;

    /**
     * Summary/excerpt of the solution
     */
    private String summary;

    /**
     * Programming language for this solution
     */
    private String language;

    /**
     * Tags associated with this solution (JSON array)
     */
    private String tags;

    /**
     * Number of views
     */
    private Integer views;

    /**
     * Number of likes (denormalized from edge_operations)
     */
    private Integer likes = 0;

    /**
     * Number of dislikes (denormalized from edge_operations)
     */
    private Integer dislikes = 0;

    /**
     * Number of comments (denormalized for performance)
     */
    private Integer commentCount = 0;

    /**
     * Whether the solution is pinned to the top
     */
    @TableField("is_pinned")
    private Boolean isPinned;

    /**
     * Whether the solution is published
     */
    @TableField("is_published")
    private Boolean isPublished;

    /**
     * When the solution was published
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * User ID who published the solution
     */
    @TableField("published_by")
    private String publishedBy;

    /**
     * Whether the solution is flagged for review
     */
    @TableField("is_flagged")
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    @TableField("flagged_reason")
    private String flaggedReason;

    /**
     * When the solution was flagged
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
     * When the solution was deleted
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /**
     * User ID who deleted the solution
     */
    @TableField("deleted_by")
    private String deletedBy;

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
}

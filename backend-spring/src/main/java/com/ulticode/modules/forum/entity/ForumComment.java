package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Forum comment entity representing the forum_comments table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("forum_comments")
public class ForumComment {

    /**
     * Comment unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the post this comment belongs to
     */
    @TableField("post_id")
    private String postId;

    /**
     * ID of the parent comment (for nested replies)
     */
    @TableField("parent_id")
    private String parentId;

    /**
     * ID of the user who created this comment
     */
    @TableField("author_id")
    private String authorId;

    /**
     * Comment body (plain text)
     */
    private String body;

    /**
     * Comment content in markdown format
     */
    private String markdown;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * When the comment was last edited
     */
    @TableField("edited_at")
    private LocalDateTime editedAt;

    /**
     * Whether the comment is pinned
     */
    @TableField("is_pinned")
    private Boolean isPinned;

    /**
     * Whether the comment is locked
     */
    @TableField("is_locked")
    private Boolean isLocked;

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

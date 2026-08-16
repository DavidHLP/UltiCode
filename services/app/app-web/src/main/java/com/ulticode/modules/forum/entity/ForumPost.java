package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Forum post entity representing the forum_posts table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName(value = "forum_posts", autoResultMap = true)
public class ForumPost {

    /**
     * Post unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the community this post belongs to
     */
    @TableField("community_id")
    private String communityId;

    /**
     * ID of the user who created this post
     */
    @TableField("user_id")
    private String userId;

    /**
     * Permalink/slug for the post
     */
    private String permalink;

    /**
     * Post title
     */
    private String title;

    /**
     * Type of flair (e.g., QUESTION, DISCUSSION, ANNOUNCEMENT)
     */
    @TableField("flair_type")
    private String flairType;

    /**
     * Label text for the flair
     */
    @TableField("flair_label")
    private String flairLabel;

    /**
     * Tags associated with this post (JSON array)
     */
    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private Object tags;

    /**
     * Excerpt/summary of the post content
     */
    private String excerpt;

    /**
     * Media attachments (JSON)
     */
    @TableField(value = "media", typeHandler = JacksonTypeHandler.class)
    private Object media;

    /**
     * Recommendation data (JSON)
     */
    @TableField(value = "recommendation", typeHandler = JacksonTypeHandler.class)
    private Object recommendation;

    /**
     * Vote state (e.g., neutral, upvoted, downvoted)
     */
    @TableField("vote_state")
    private String voteState;

    /**
     * Whether the post is saved/bookmarked
     */
    @TableField("is_saved")
    private Boolean isSaved;

    /**
     * Number of impressions/views
     */
    private Integer impressions;

    /**
     * Whether the post is pinned
     */
    @TableField("is_pinned")
    private Boolean isPinned;

    /**
     * Whether the post is locked (no comments allowed)
     */
    @TableField("is_locked")
    private Boolean isLocked;

    /**
     * Post statistics (JSON)
     */
    @TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
    private Object stats;

    /**
     * Number of views
     */
    private Integer views;

    /**
     * Whether the post is flagged for review
     */
    @TableField("is_flagged")
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    @TableField("flagged_reason")
    private String flaggedReason;

    /**
     * When the post was flagged
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
     * When the post was deleted
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /**
     * User ID who deleted the post
     */
    @TableField("deleted_by")
    private String deletedBy;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp (SEARCH-003 backfill version source)
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

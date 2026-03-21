package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ModerationQueue entity representing the moderation_queue table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("moderation_queue")
public class ModerationQueue {

    /**
     * Queue item unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Type of the reported entity (e.g., ForumPost, Solution, Comment)
     */
    @TableField("entity_type")
    private String entityType;

    /**
     * ID of the reported entity
     */
    @TableField("entity_id")
    private String entityId;

    /**
     * ID of the author of the reported content
     */
    @TableField("author_id")
    private String authorId;

    /**
     * Priority level (higher = more urgent)
     */
    private Integer priority;

    /**
     * Current status of the moderation item
     */
    private String status;

    /**
     * Number of reports for this item
     */
    @TableField("report_count")
    private Integer reportCount;

    /**
     * Primary category of reports
     */
    @TableField("primary_category")
    private String primaryCategory;

    /**
     * ID of the moderator assigned to this item
     */
    @TableField("assigned_to_id")
    private String assignedToId;

    /**
     * When the item was assigned
     */
    @TableField("assigned_at")
    private LocalDateTime assignedAt;

    /**
     * ID of the moderator who reviewed this item
     */
    @TableField("reviewed_by_id")
    private String reviewedById;

    /**
     * When the item was reviewed
     */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Resolution type (e.g., DELETED, WARNED, DISMISSED)
     */
    private String resolution;

    /**
     * Additional notes about the resolution
     */
    @TableField("resolution_note")
    private String resolutionNote;

    /**
     * Record creation timestamp (auto-filled)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp (auto-filled)
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * When the item was resolved
     */
    @TableField("resolved_at")
    private LocalDateTime resolvedAt;
}

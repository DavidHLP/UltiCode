package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Report entity representing the reports table.
 * Individual reports submitted by users.
 */
@Data
@TableName("reports")
public class Report {

    /**
     * Report unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the user who submitted the report
     */
    @TableField("reporter_id")
    private String reporterId;

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
     * Category of the report (e.g., SPAM, HARASSMENT)
     */
    private String category;

    /**
     * Detailed reason for the report
     */
    private String reason;

    /**
     * Evidence provided by the reporter
     */
    private String evidence;

    /**
     * Current status of the report
     */
    private String status;

    /**
     * ID of the moderation queue item this report belongs to
     */
    @TableField("queue_id")
    private String queueId;

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
}

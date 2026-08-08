package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ModerationAction entity representing the moderation_actions table.
 * Records all moderation actions taken on queue items.
 */
@Data
@TableName("moderation_actions")
public class ModerationAction {

    /**
     * Action unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the moderation queue item
     */
    @TableField("queue_id")
    private String queueId;

    /**
     * Type of action taken (e.g., DELETED, WARNED, DISMISSED)
     */
    @TableField("action")
    private String action;

    /**
     * ID of the moderator who performed the action
     */
    @TableField("performed_by_id")
    private String performedById;

    /**
     * Additional notes about the action
     */
    private String note;

    /**
     * Duration in days (for temporary bans)
     */
    @TableField("duration_days")
    private Integer durationDays;

    /**
     * Record creation timestamp (auto-filled)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Appeal entity representing the appeals table.
 * Appeals submitted by users against moderation decisions.
 */
@Data
@TableName("appeals")
public class Appeal {

    /**
     * Appeal unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the moderation queue item being appealed
     */
    @TableField("queue_id")
    private String queueId;

    /**
     * ID of the user submitting the appeal
     */
    @TableField("appellant_id")
    private String appellantId;

    /**
     * Reason for the appeal
     */
    private String reason;

    /**
     * Evidence provided by the appellant
     */
    private String evidence;

    /**
     * Current status of the appeal
     */
    private String status;

    /**
     * ID of the moderator who reviewed the appeal
     */
    @TableField("reviewed_by_id")
    private String reviewedById;

    /**
     * When the appeal was reviewed
     */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Response from the moderator
     */
    private String response;

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

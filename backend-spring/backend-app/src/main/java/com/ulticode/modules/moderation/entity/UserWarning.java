package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UserWarning entity representing the user_warnings table.
 * Records warnings issued to users.
 */
@Data
@TableName("user_warnings")
public class UserWarning {

    /**
     * Warning unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the warned user
     */
    @TableField("user_id")
    private String userId;

    /**
     * ID of the moderation queue item that triggered the warning
     */
    @TableField("queue_id")
    private String queueId;

    /**
     * Reason for the warning
     */
    private String reason;

    /**
     * ID of the moderation action that triggered this warning
     */
    @TableField("action_id")
    private String actionId;

    /**
     * Category of the warning (e.g., SPAM, HARASSMENT)
     */
    private String category;

    /**
     * Whether the warning has been acknowledged by the user
     */
    @TableField("acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /**
     * Record creation timestamp (auto-filled)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * When the warning expires (null = never expires)
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;
}

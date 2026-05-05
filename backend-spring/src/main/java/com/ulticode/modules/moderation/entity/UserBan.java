package com.ulticode.modules.moderation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UserBan entity representing the user_bans table.
 * Records bans issued to users.
 */
@Data
@TableName("user_bans")
public class UserBan {

    /**
     * Ban unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the banned user
     */
    @TableField("user_id")
    private String userId;

    /**
     * ID of the moderation queue item that triggered the ban
     */
    @TableField("queue_id")
    private String queueId;

    /**
     * ID of the moderation action that triggered this ban
     */
    @TableField("action_id")
    private String actionId;

    /**
     * Reason for the ban
     */
    private String reason;

    /**
     * Category of the ban (e.g., SPAM, HARASSMENT)
     */
    private String category;

    /**
     * ID of the moderator who issued the ban
     */
    @TableField("banned_by_id")
    private String bannedById;

    /**
     * When the ban started
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * When the ban expires (null for permanent bans)
     */
    @TableField("ends_at")
    private LocalDateTime endsAt;

    /**
     * When the ban was lifted (null if still active)
     */
    @TableField("unbanned_at")
    private LocalDateTime unbannedAt;

    /**
     * ID of the moderator who lifted the ban
     */
    @TableField("unbanned_by_id")
    private String unbannedById;

    /**
     * Reason for unbanning
     */
    @TableField("unban_reason")
    private String unbanReason;

    /**
     * Whether this is a permanent ban
     */
    @TableField("is_permanent")
    private Boolean isPermanent;

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

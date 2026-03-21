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
     * Reason for the ban
     */
    private String reason;

    /**
     * ID of the moderator who issued the ban
     */
    @TableField("issued_by_id")
    private String issuedById;

    /**
     * Record creation timestamp (auto-filled)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * When the ban expires (null for permanent bans)
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * Whether this is a permanent ban
     */
    @TableField("is_permanent")
    private Boolean isPermanent;
}

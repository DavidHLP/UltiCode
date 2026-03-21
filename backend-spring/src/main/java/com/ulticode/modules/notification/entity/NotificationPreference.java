package com.ulticode.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Notification preference entity - matches notification_preferences table in Prisma schema.
 */
@Data
@TableName("notification_preferences")
public class NotificationPreference {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private Boolean communication;
    private Boolean marketing;
    private Boolean security;
    private Boolean system;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

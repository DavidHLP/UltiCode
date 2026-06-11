package com.ulticode.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Notification preference entity - matches notification_preferences table.
 *
 * <p>Field {@code systemEnabled} maps to the {@code system_enabled} column.
 * The original column name {@code system} collided with MySQL 9.x reserved
 * keyword and was renamed via {@code V20260611120000}.
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

    @TableField("system_enabled")
    private Boolean systemEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

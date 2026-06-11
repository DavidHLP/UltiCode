package com.ulticode.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notification entity - matches notifications table.
 *
 * <p>Soft-delete via {@code @TableLogic} on {@code deleted} → maps to
 * {@code is_deleted} column (Q12 fix). {@code BaseMapper.deleteById},
 * {@code selectById}, and {@code selectPage} with {@code LambdaQueryWrapper}
 * auto-filter {@code is_deleted=0}; explicit SQL (e.g.
 * {@code countUnreadByUserId}, {@code markAllAsRead}) must include the
 * predicate manually.
 */
@Data
@TableName(value = "notifications", autoResultMap = true)
public class Notification {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String type;

    private String category;

    private String title;
    private String body;
    private String link;

    private String announcementId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    private Boolean isRead;
    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}

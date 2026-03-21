package com.ulticode.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notification entity - matches notifications table in Prisma schema.
 */
@Data
@TableName(value = "notifications", autoResultMap = true)
public class Notification {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    @TableField("type")
    private String type;

    @TableField("category")
    private String category;

    private String title;
    private String body;
    private String link;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    private Boolean isRead;
    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

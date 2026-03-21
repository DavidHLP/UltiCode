package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Forum tag entity representing the forum_tags table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("forum_tags")
public class ForumTag {

    /**
     * Tag unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Tag display name
     */
    private String name;

    /**
     * URL-friendly slug (unique)
     */
    private String slug;

    /**
     * Tag description
     */
    private String description;

    /**
     * Tag color (hex code)
     */
    private String color;

    /**
     * Number of times this tag has been used
     */
    @TableField("usage_count")
    private Integer usageCount;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

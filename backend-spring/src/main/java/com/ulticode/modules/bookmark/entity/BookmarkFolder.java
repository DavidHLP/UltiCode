package com.ulticode.modules.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bookmark folder entity representing the collections table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("collections")
public class BookmarkFolder {

    /**
     * Folder unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the user who owns this folder
     */
    @TableField("user_id")
    private String userId;

    /**
     * Folder name
     */
    private String name;

    /**
     * Folder description
     */
    private String description;

    /**
     * Icon for the folder
     */
    private String icon;

    /**
     * Color for the folder
     */
    private String color;

    /**
     * Sort order for display
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * Whether this is the default folder
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Record update timestamp
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

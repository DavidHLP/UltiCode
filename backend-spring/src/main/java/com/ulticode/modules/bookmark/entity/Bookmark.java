package com.ulticode.modules.bookmark.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bookmark entity representing the collection_items table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("collection_items")
public class Bookmark {

    /**
     * Bookmark unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the folder this bookmark belongs to
     */
    @TableField("collection_id")
    private String folderId;

    /**
     * ID of the target item (problem, solution, post, etc.)
     */
    @TableField("target_id")
    private String targetId;

    /**
     * Type of the target item
     */
    @TableField("target_type")
    private String targetType;

    /**
     * Sort order within the folder
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * Optional note for this bookmark
     */
    private String note;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

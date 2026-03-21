package com.ulticode.modules.problemlist.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem list category entity for user-defined categories.
 *
 * NOTE: This table does NOT exist in the current Prisma schema.
 * Implementation requires database migration:
 *
 * CREATE TABLE problem_list_categories (
 *   id VARCHAR(36) PRIMARY KEY,
 *   user_id VARCHAR(36) NOT NULL,
 *   name VARCHAR(100) NOT NULL,
 *   description TEXT,
 *   icon VARCHAR(50),
 *   color VARCHAR(20),
 *   sort_order INT DEFAULT 0,
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *   INDEX idx_user_id (user_id)
 * );
 */
@Data
@TableName("problem_list_categories")
public class ProblemListCategory {

    /**
     * Category unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the user who owns this category
     */
    @TableField("user_id")
    private String userId;

    /**
     * Name of the category
     */
    private String name;

    /**
     * Description of the category
     */
    private String description;

    /**
     * Icon for the category
     */
    private String icon;

    /**
     * Color for the category
     */
    private String color;

    /**
     * Sort order for display
     */
    @TableField("sort_order")
    private Integer sortOrder;

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

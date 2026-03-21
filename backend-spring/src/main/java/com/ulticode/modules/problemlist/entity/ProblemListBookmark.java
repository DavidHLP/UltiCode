package com.ulticode.modules.problemlist.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem list bookmark entity for saved problem lists.
 *
 * NOTE: This table does NOT exist in the current Prisma schema.
 * Implementation requires database migration:
 *
 * CREATE TABLE problem_list_bookmarks (
 *   id VARCHAR(36) PRIMARY KEY,
 *   user_id VARCHAR(36) NOT NULL,
 *   list_id VARCHAR(36) NOT NULL,
 *   category_id VARCHAR(36),
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *   INDEX idx_user_id (user_id),
 *   INDEX idx_list_id (list_id),
 *   INDEX idx_category_id (category_id),
 *   UNIQUE KEY uk_user_list (user_id, list_id)
 * );
 */
@Data
@TableName("problem_list_bookmarks")
public class ProblemListBookmark {

    /**
     * Bookmark unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the user who saved this list
     */
    @TableField("user_id")
    private String userId;

    /**
     * ID of the saved problem list
     */
    @TableField("list_id")
    private String listId;

    /**
     * ID of the category this bookmark belongs to (optional)
     */
    @TableField("category_id")
    private String categoryId;

    /**
     * Record creation timestamp
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

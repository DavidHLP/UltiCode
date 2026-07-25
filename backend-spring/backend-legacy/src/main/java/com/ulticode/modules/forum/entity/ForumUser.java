package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ForumUser entity representing the forum_users table.
 * This is a separate user system for the forum module.
 */
@Data
@TableName("forum_users")
public class ForumUser {

    /**
     * Forum user ID (separate from main users table ID).
     * Format varies: could be short IDs like 'u-001' or UUIDs.
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * Username for display in forum.
     */
    private String username;

    /**
     * Avatar URL.
     */
    private String avatar;

    /**
     * Karma score.
     */
    private Integer karma;

    /**
     * Creation timestamp.
     * Note: This field does NOT exist in the database table.
     * Used only for internal tracking.
     */
    @TableField(exist = false)
    private LocalDateTime createdAt;
}

package com.ulticode.modules.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User entity representing the users table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("users")
public class User {

    /**
     * User unique identifier (UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * Unique username
     */
    private String username;

    /**
     * Display name
     */
    private String name;

    /**
     * Email address
     */
    private String email;

    /**
     * Avatar URL
     */
    private String avatar;

    /**
     * Hashed password
     */
    private String password;

    /**
     * User biography
     */
    private String bio;

    /**
     * Company name
     */
    private String company;

    /**
     * GitHub profile URL
     */
    private String github;

    /**
     * User registration timestamp
     */
    @TableField("joined_at")
    private LocalDateTime joinedAt;

    /**
     * User location
     */
    private String location;

    /**
     * Twitter profile URL
     */
    private String twitter;

    /**
     * Personal website URL
     */
    private String website;

    /**
     * Preferred programming language
     */
    @TableField("preferred_language")
    private String preferredLanguage;

    /**
     * User role (USER, ADMIN, SUPER_ADMIN)
     */
    private String role;

    /**
     * Whether the user account is active
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * Whether the user is banned
     */
    @TableField("is_banned")
    private Boolean isBanned;

    /**
     * Ban expiration timestamp
     */
    @TableField("banned_until")
    private LocalDateTime bannedUntil;

    /**
     * Reason for ban
     */
    @TableField("banned_reason")
    private String bannedReason;

    /**
     * Last login timestamp
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * ID of user who created this record
     */
    @TableField("created_by")
    private String createdBy;

    /**
     * ID of user who last updated this record
     */
    @TableField("updated_by")
    private String updatedBy;

    /**
     * Record creation timestamp (auto-filled)
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp (auto-filled)
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Soft delete flag
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
}

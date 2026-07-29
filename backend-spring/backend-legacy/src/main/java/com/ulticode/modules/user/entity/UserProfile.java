package com.ulticode.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User profile entity representing the {@code user_profiles} table (P5-USERPROFILE-001).
 *
 * <p>This table holds the profile subset of the former {@code users} table:
 * display name, avatar, bio, social links, and preferred language.
 * The account side (credentials, role, ban status) remains in {@code users}
 * owned by Auth. This entity is owned by the App domain.
 */
@Data
@TableName("user_profiles")
public class UserProfile {

    @TableId(type = IdType.INPUT)
    @TableField("account_id")
    private String accountId;

    private String name;

    private String avatar;

    private String bio;

    private String company;

    private String github;

    private String location;

    private String twitter;

    private String website;

    @TableField("preferred_language")
    private String preferredLanguage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

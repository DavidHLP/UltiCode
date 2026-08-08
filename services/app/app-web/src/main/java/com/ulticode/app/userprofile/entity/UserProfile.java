package com.ulticode.app.userprofile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * App-owned profile entity mapping to the {@code user_profiles} table.
 *
 * <p>Created in {@code backend-app} as part of P7-APP-USERPROFILE-001.
 * The {@code accountId} field is the shared key with the Auth-owned
 * {@code users.id} (JWT {@code sub}).
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

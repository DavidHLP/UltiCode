package com.ulticode.auth.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User permission entity (user_permissions table).
 */
@Data
@TableName("user_permissions")
public class UserPermission {

    @TableId(type = IdType.INPUT)
    private String id;

    /** Authorized user ID */
    private String userId;

    /** Resource type (USER, PROBLEM, CONTEST, SOLUTION, FORUM_POST, FORUM_COMMENT, SYSTEM, PROBLEM_LIST, TAG) */
    private String resource;

    /** Action type (CREATE, READ, UPDATE, DELETE, MODERATE, PUBLISH, MANAGE_USERS, MANAGE_PERMISSIONS) */
    private String action;

    /** Grantor user ID */
    private String grantedBy;

    /** Granted timestamp */
    private LocalDateTime grantedAt;

    /** Expiration timestamp, null = permanent */
    private LocalDateTime expiresAt;
}

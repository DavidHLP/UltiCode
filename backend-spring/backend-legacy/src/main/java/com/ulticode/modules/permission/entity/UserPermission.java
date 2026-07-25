package com.ulticode.modules.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户权限实体 (user_permissions 表)
 *
 * <p>对应直接授予用户的细粒度权限,与 role_permissions 表互补 (合并后即为用户最终权限集)。
 *
 * <p>字段顺序与 production DDL (V20260602_120000) 保持一致以方便对照排查。
 */
@Data
@TableName("user_permissions")
public class UserPermission {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 被授权的用户 ID */
    private String userId;

    /** 资源类型 (ENUM: USER/PROBLEM/CONTEST/SOLUTION/FORUM_POST/FORUM_COMMENT/SYSTEM/PROBLEM_LIST/TAG) */
    private String resource;

    /** 操作类型 (ENUM: CREATE/READ/UPDATE/DELETE/MODERATE/PUBLISH/MANAGE_USERS/MANAGE_PERMISSIONS) */
    private String action;

    /** 授权人 ID (NOT NULL, 通常为管理员或 'system') */
    private String grantedBy;

    /** 授权时间 (数据库默认 CURRENT_TIMESTAMP) */
    private LocalDateTime grantedAt;

    /** 过期时间, null = 永久授权 */
    private LocalDateTime expiresAt;
}

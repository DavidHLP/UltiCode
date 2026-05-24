package com.ulticode.modules.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 角色权限实体
 */
@Data
@TableName("role_permissions")
public class RolePermission {

    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 角色 (USER, MODERATOR, ADMIN, SUPER_ADMIN)
     */
    private String role;

    /**
     * 权限操作 (CREATE, READ, UPDATE, DELETE, MODERATE, PUBLISH, MANAGE_USERS, MANAGE_PERMISSIONS)
     */
    private String action;

    /**
     * 权限资源 (USER, PROBLEM, CONTEST, SOLUTION, FORUM_POST, FORUM_COMMENT, SOLUTION_COMMENT, SYSTEM, PROBLEM_LIST, TAG)
     */
    private String resource;
}

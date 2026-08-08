package com.ulticode.auth.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Role permission entity (role_permissions table).
 */
@Data
@TableName("role_permissions")
public class RolePermission {

    @TableId(type = IdType.INPUT)
    private String id;

    /** Role name (USER, MODERATOR, ADMIN, SUPER_ADMIN) */
    private String role;

    /** Permission action */
    private String action;

    /** Permission resource */
    private String resource;
}

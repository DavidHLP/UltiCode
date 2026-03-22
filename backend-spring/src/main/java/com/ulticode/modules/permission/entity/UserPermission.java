package com.ulticode.modules.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户权限实体
 */
@Data
@TableName("user_permissions")
public class UserPermission {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;

    private String resource;

    private String action;

    private LocalDateTime grantedAt;
}

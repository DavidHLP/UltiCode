package com.ulticode.modules.follow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User follow relationship entity.
 * Composite key is (followerId, followingId).
 */
@Data
@TableName("user_follows")
public class UserFollow {

    @TableId(type = IdType.INPUT)
    private String followerId;

    private String followingId;

    private LocalDateTime createdAt;
}

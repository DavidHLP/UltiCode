package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Forum community member entity representing the forum_community_members table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("forum_community_members")
public class ForumCommunityMember {

    /**
     * Member record unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * ID of the community
     */
    @TableField("community_id")
    private String communityId;

    /**
     * ID of the user
     */
    @TableField("user_id")
    private String userId;

    /**
     * Member role (MEMBER, MODERATOR, ADMIN, OWNER)
     */
    private String role;

    /**
     * When the user joined the community
     */
    @TableField("joined_at")
    private LocalDateTime joinedAt;
}

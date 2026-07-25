package com.ulticode.modules.forum.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Forum community entity representing the forum_communities table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("forum_communities")
public class ForumCommunity {

    /**
     * Community unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Community display name
     */
    private String name;

    /**
     * URL-friendly slug (unique)
     */
    private String slug;

    /**
     * Community description
     */
    private String description;

    /**
     * Number of members
     */
    private Integer members;

    /**
     * Number of online members
     */
    private Integer online;

    /**
     * Icon URL or identifier
     */
    private String icon;

    /**
     * Theme color (hex code)
     */
    private String color;

    /**
     * Banner image URL
     */
    private String banner;

    /**
     * Total number of posts
     */
    @TableField("posts_count")
    private Integer postsCount;

    /**
     * Number of posts today
     */
    @TableField("posts_today")
    private Integer postsToday;

    /**
     * Number of posts this week
     */
    @TableField("posts_week")
    private Integer postsWeek;

    /**
     * Whether this is an official community
     */
    @TableField("is_official")
    private Boolean isOfficial;

    /**
     * Whether this community is featured
     */
    @TableField("is_featured")
    private Boolean isFeatured;

    /**
     * Sort order for display
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Community visibility (PUBLIC, PRIVATE)
     */
    private String visibility;
}

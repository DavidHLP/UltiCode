package com.ulticode.modules.problemlist.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem list entity representing the problem_lists table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("problem_lists")
public class ProblemList {

    /**
     * Problem list unique identifier (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Name of the problem list
     */
    private String name;

    /**
     * Description of the problem list
     */
    private String description;

    /**
     * ID of the user who created this list
     */
    @TableField("author_id")
    private String authorId;

    /**
     * Whether this list is public
     */
    @TableField("is_public")
    private Boolean isPublic;

    /**
     * Whether this list is featured
     */
    @TableField("is_featured")
    private Boolean isFeatured;

    /**
     * Banner tag for display
     */
    @TableField("banner_tag")
    private String bannerTag;

    /**
     * Banner icon for display
     */
    @TableField("banner_icon")
    private String bannerIcon;

    /**
     * Banner theme for display
     */
    @TableField("banner_theme")
    private String bannerTheme;

    /**
     * Banner display order
     */
    @TableField("banner_order")
    private Integer bannerOrder;

    /**
     * Version for optimistic locking
     */
    @Version
    private Integer version;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Record update timestamp
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

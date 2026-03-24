package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem tag entity for categorizing problems.
 */
@Data
@TableName("problem_tags")
public class ProblemTag {

    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * Display label for the tag
     */
    private String label;

    /**
     * URL-friendly slug
     */
    private String slug;

    /**
     * Color for UI display
     */
    private String color;

    /**
     * Tag description
     */
    private String description;

    /**
     * Number of problems using this tag
     */
    @TableField("usage_count")
    private Integer usageCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

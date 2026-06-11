package com.ulticode.modules.achievement.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Achievement entity - represents a defined achievement/badge.
 */
@Data
@TableName(value = "achievements", autoResultMap = true)
public class Achievement {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** Unique key for the achievement (MySQL reserved word; column backticked via @TableField) */
    @TableField(value = "`key`")
    private String key;

    /** Display name of the achievement */
    private String name;

    /** Description of the achievement */
    private String description;

    /** Icon URL or identifier */
    private String icon;

    /** Category of the achievement (e.g., problem_solving, consistency, contest) */
    private String category;

    /** Tier level (1=bronze, 2=silver, 3=gold, 4=platinum) */
    private Integer tier;

    /** Criteria for earning this achievement (JSON: type, target) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> criteria;

    /** Points awarded for this achievement */
    private Integer points;

    /** Whether the achievement is active */
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

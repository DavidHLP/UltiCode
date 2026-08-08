package com.ulticode.app.i18n.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Translation entity for storing internationalized content.
 * <p>
 * Maps to the 'translations' table in the database.
 */
@Data
@TableName("translations")
public class Translation {

    /**
     * Unique identifier for the translation.
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Type of the entity being translated.
     * Values: PROBLEM, PROBLEM_DETAIL, CONTEST, SOLUTION, POST
     */
    private String entityType;

    /**
     * ID of the entity being translated.
     */
    private String entityId;

    /**
     * Name of the field being translated.
     * Examples: title, summary, description, hints, solution, content
     */
    private String fieldName;

    /**
     * Locale code for the translation.
     * Examples: en-US, zh-CN, zh-TW, ja-JP
     */
    private String locale;

    /**
     * The translated content.
     */
    private String content;

    /**
     * ID of the user who created this translation.
     */
    private String createdBy;

    /**
     * ID of the user who last updated this translation.
     */
    private String updatedBy;

    /**
     * Timestamp when the translation was created.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the translation was last updated.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

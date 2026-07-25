package com.ulticode.modules.i18n.entity;

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
    @Column(length = 40)
    private String id;

    /**
     * Type of the entity being translated.
     * Values: PROBLEM, PROBLEM_DETAIL, CONTEST, SOLUTION, POST
     */
    @Column(length = 50)
    private String entityType;

    /**
     * ID of the entity being translated.
     */
    @Column(length = 50)
    private String entityId;

    /**
     * Name of the field being translated.
     * Examples: title, summary, description, hints, solution, content
     */
    @Column(length = 50)
    private String fieldName;

    /**
     * Locale code for the translation.
     * Examples: en-US, zh-CN, zh-TW, ja-JP
     */
    @Column(length = 10)
    private String locale;

    /**
     * The translated content.
     */
    private String content;

    /**
     * ID of the user who created this translation.
     */
    @Column(length = 40)
    private String createdBy;

    /**
     * ID of the user who last updated this translation.
     */
    @Column(length = 40)
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

    /**
     * Simple column annotation for documentation purposes.
     */
    @interface Column {
        int length() default 255;
    }
}

package com.ulticode.modules.email.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Email template entity.
 * Stores reusable email templates with variable placeholders.
 */
@Data
@TableName(value = "email_templates", autoResultMap = true)
public class EmailTemplate {

    /**
     * Unique template identifier
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Template name
     */
    private String name;

    /**
     * Email subject (can contain {{variable}} placeholders)
     */
    private String subject;

    /**
     * Email body in HTML format (can contain {{variable}} placeholders)
     */
    private String body;

    /**
     * List of variable names used in the template
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> variables;

    /**
     * Template creation timestamp
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Template last update timestamp
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.ulticode.modules.solution.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Solution topic entity (solution_topics table).
 * Used by SolutionTopicController.listTopics() to feed
 * frontend SolutionsEditView topic picker.
 */
@Data
@TableName("solution_topics")
public class SolutionTopic {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String slug;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("is_active")
    private Boolean active;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

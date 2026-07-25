package com.ulticode.modules.problemlist.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Problem list problem relation entity representing the problem_list_problem_relations table.
 * This is a join table with composite key (list_id, problem_id).
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("problem_list_problem_relations")
public class ProblemListProblemRelation {

    /**
     * ID of the problem list
     */
    @TableField("list_id")
    private String listId;

    /**
     * ID of the problem
     */
    private Long problemId;

    /**
     * Sort order within the list
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * When the problem was added to the list
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime addedAt;
}

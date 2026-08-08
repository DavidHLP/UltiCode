package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Problem detail entity for extended problem information.
 */
@Data
@TableName("problem_details")
public class ProblemDetail {

    /**
     * Empty JSON array literal for initializing JSON NOT NULL columns
     * (e.g. {@code constraints_json}) on insert, since the column has no
     * database default and callers may not always supply a value.
     */
    public static final String EMPTY_JSON_ARRAY = "[]";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("problem_id")
    private Long problemId;

    private String slug;

    /**
     * Problem summary/description
     */
    private String summary;

    private String content;

    /**
     * Companies associated with this problem (JSON)
     */
    private String companies;

    private Integer likes;

    private Integer dislikes;

    @TableField("difficulty_rating")
    private BigDecimal difficultyRating;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Follow-up questions
     */
    private String followUp;

    /**
     * Problem constraints (JSON array)
     */
    @TableField("constraints_json")
    private String constraintsJson;

    /**
     * Hints for solving (JSON array)
     */
    private String hints;

    /**
     * Interactions data (JSON)
     */
    private String interactions;
}

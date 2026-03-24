package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * Problem language entity for starter code in different programming languages.
 */
@Data
@TableName("problem_languages")
public class ProblemLanguage {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("problem_id")
    private Long problemId;

    /**
     * Display label (e.g., "C++", "Java", "Python")
     */
    private String label;

    /**
     * Language identifier (e.g., "cpp", "java", "python")
     */
    private String value;

    /**
     * Code editor style (e.g., language mode for syntax highlighting)
     */
    private String style;

    /**
     * Starter code template
     */
    @TableField("starter_code")
    private String starterCode;
}

package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * Problem example entity for test examples shown to users.
 */
@Data
@TableName("problem_examples")
public class ProblemExample {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("problem_id")
    private Long problemId;

    /**
     * Order of the example (1, 2, 3...)
     */
    @TableField("example_order")
    private Integer exampleOrder;

    /**
     * Input text for the example
     */
    @TableField("input_text")
    private String inputText;

    /**
     * Output text for the example
     */
    @TableField("output_text")
    private String outputText;

    /**
     * Explanation of the example
     */
    private String explanation;

    /**
     * Structured inputs (JSON)
     */
    private String inputs;
}

package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Test case entity for judge system test cases.
 */
@Data
@TableName("test_cases")
public class TestCase {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("problem_id")
    private Long problemId;

    /**
     * Whether this is a sample test case
     */
    @TableField("is_sample")
    private Boolean isSample;

    /**
     * Whether this test case is hidden from users
     */
    @TableField("is_hidden")
    private Boolean isHidden;

    /**
     * Order of the test case
     */
    @TableField("test_order")
    private Integer testOrder;

    /**
     * Input text
     */
    @TableField("input_text")
    private String inputText;

    /**
     * Expected output text
     */
    @TableField("output_text")
    private String outputText;

    /**
     * Structured inputs as JSON
     */
    private String inputs;

    /**
     * Explanation of the test case
     */
    private String explanation;

    /**
     * Additional constraints (JSON)
     */
    private String constraints;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Soft delete flag. Mirrors submissions / problems table pattern — when
     * set to true, MyBatis-Plus auto-filters the record out of all queries
     * and deleteById() performs an UPDATE is_deleted=1 instead of a row
     * removal. See {@code V20260610130000__Add_Test_Cases_Is_Deleted.sql}.
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    /**
     * Timestamp of soft deletion. Null when the record is still active.
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}

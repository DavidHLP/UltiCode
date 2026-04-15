package com.ulticode.modules.submission.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Submission entity representing the submissions table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName(value = "submissions", autoResultMap = true)
public class Submission {

    /**
     * Submission unique identifier (UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * ID of the problem being submitted
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * ID of the user who submitted
     */
    @TableField("user_id")
    private String userId;

    /**
     * Programming language used (e.g., javascript, python, java)
     */
    private String language;

    /**
     * Source code submitted
     */
    private String code;

    /**
     * Submission status (e.g., Pending, Accepted, Wrong Answer)
     */
    private String status;

    /**
     * Runtime in milliseconds
     */
    private Integer runtime;

    /**
     * Memory usage in megabytes
     */
    private Double memory;

    /**
     * Additional notes
     */
    private String notes;

    /**
     * Number of times this submission has been rejudged by an admin.
     * Incremented each time an admin triggers a rejudge.
     */
    @TableField("retry_count")
    private Integer retryCount = 0;

    /**
     * Submission creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Runtime percentile compared to other submissions
     */
    @TableField("runtime_percentile")
    private Double runtimePercentile;

    /**
     * Memory percentile compared to other submissions
     */
    @TableField("memory_percentile")
    private Double memoryPercentile;

    /**
     * Test case execution details (JSON)
     */
    @TableField(value = "test_details", typeHandler = JacksonTypeHandler.class)
    private List<TestCaseDetail> testDetails;

    /**
     * Memory distribution bins in MB (JSON)
     */
    @TableField(value = "memoryDistBinsMb", typeHandler = JacksonTypeHandler.class)
    private Object memoryDistBinsMb;

    /**
     * Runtime distribution bins in ms (JSON)
     */
    @TableField(value = "runtimeDistBinsMs", typeHandler = JacksonTypeHandler.class)
    private Object runtimeDistBinsMs;

    /**
     * Nested class for test case details
     */
    @Data
    public static class TestCaseDetail {
        private String status;
        private Integer time;
        private Double memory;
        private String detail;
        private String output;
        private String expectedOutput;
        private List<InputParam> inputs;

        @Data
        public static class InputParam {
            private String id;
            private String label;
            private String name;
            private String value;
        }
    }
}

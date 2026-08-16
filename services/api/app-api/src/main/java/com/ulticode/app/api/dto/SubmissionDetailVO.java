package com.ulticode.app.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.io.Serializable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full submission detail DTO.
 * Includes all fields for detailed submission view.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;


    private String id;
    private Long problemId;
    private String userId;
    private String language;
    private String status;
    private Integer runtime;
    private Double memory;
    private String notes;
    private LocalDateTime createdAt;
    private Double runtimePercentile;
    private Double memoryPercentile;

    /**
     * Source code submitted
     */
    private String code;

    /**
     * Test case results
     */
    private List<TestResult> tests;

    /**
     * Compiler error message (if any)
     */
    private String compilerError;

    /**
     * Error detail message
     */
    private String errorDetail;

    /**
     * Formatted input that caused failure
     */
    private String input;

    /**
     * Actual output from the failed test
     */
    private String output;

    /**
     * Expected output for the failed test
     */
    private String expectedOutput;

    /**
     * User info (simplified)
     */
    private UserInfo user;

    /**
     * Problem info (simplified)
     */
    private ProblemInfo problem;

    /**
     * Runtime distribution bins in milliseconds (numeric array)
     */
    private List<Integer> runtimeDistBinsMs;

    /**
     * Memory distribution bins in MB (numeric array)
     */
    private List<Integer> memoryDistBinsMb;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String username;
        private String name;
        private String avatar;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProblemInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String title;
        private String slug;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TestResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String status;
        private Integer runtime;
        private Double memory;
    }
}

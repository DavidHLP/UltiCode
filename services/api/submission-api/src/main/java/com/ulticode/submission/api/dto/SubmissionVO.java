package com.ulticode.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.io.Serializable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Submission View Object for API responses.
 * Contains all fields needed for the frontend.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionVO implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * Submission unique identifier
     */
    private String id;

    /**
     * ID of the problem being submitted
     */
    private Long problemId;

    /**
     * ID of the user who submitted
     */
    private String userId;

    /**
     * Programming language used
     */
    private String language;

    /**
     * Source code submitted
     */
    private String code;

    /**
     * Submission status
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
     * Submission creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Runtime percentile
     */
    private Double runtimePercentile;

    /**
     * Memory percentile
     */
    private Double memoryPercentile;

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
     * User information nested class
     */
    @Data
    public static class UserInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String username;
        private String name;
        private String avatar;
    }

    /**
     * Problem information nested class
     */
    @Data
    public static class ProblemInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String title;
        private String slug;
    }

    /**
     * Test result nested class
     */
    @Data
    public static class TestResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String status;
        private Integer runtime;
        private Double memory;
    }

    /**
     * Memory distribution bins in MB (numeric array, serialized as JSON array)
     */
    private List<Integer> memoryDistBinsMb;
}

package com.ulticode.modules.submission.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of running user-supplied code against user-supplied test cases.
 *
 * <p>Two parallel representations of runtime/memory are exposed for the
 * frontend to choose from:
 * <ul>
 *   <li>{@code runtime} / {@code memory}: pre-formatted strings such as
 *       {@code "12ms"} / {@code "22.0MB"} — convenient for display.</li>
 *   <li>{@code runtimeMs} / {@code memoryMb}: numeric values — convenient
 *       for charts and aggregation. (Added in v2; absent for legacy callers.)</li>
 * </ul>
 *
 * <p>{@code verdict} is the per-run overall status; per-case status lives
 * inside each {@link RunCaseResult#status} field.
 *
 * @see docs/reports/submission-api-test-report-2026-06-10.md §4.1
 */
@Data
@Builder
public class RunResultDTO {

    private String id;
    private Long problemId;
    private String userId;
    private String verdict;
    private String runtime;
    private String memory;

    /** Runtime in milliseconds (numeric, v2 schema). */
    private Long runtimeMs;
    /** Memory in MB (numeric, v2 schema). */
    private Double memoryMb;

    private List<RunCaseResult> cases;
    private int passedCases;
    private int totalCases;
    private String errorMessage;

    @Data
    @Builder
    public static class RunCaseResult {
        private String id;
        private String runId;
        private String submissionTestId;
        private String testCaseId;
        private String caseLabel;
        private String status;
        private String runtime;
        private String memory;
        /** Runtime in milliseconds (numeric, v2 schema). */
        private Long runtimeMs;
        /** Memory in MB (numeric, v2 schema). */
        private Double memoryMb;
        private String detail;
        private String output;
        private String expectedOutput;
        private List<InputParam> inputs;

        @Data
        @Builder
        public static class InputParam {
            private String id;
            private String label;
            private String name;
            private String value;
        }
    }
}

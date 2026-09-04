package com.ulticode.modules.submission.runtime;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** Runtime-private result model; provider maps it to JudgeRunResult. */
@Data
@Builder
public class JudgeRunResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private Long problemId;
    private String userId;
    private String verdict;
    private String runtime;
    private String memory;
    private Long runtimeMs;
    private Double memoryMb;
    private Long runtimeUs;
    private Long cpuMs;
    private List<RunCaseResult> cases;
    private int passedCases;
    private int totalCases;
    private String errorMessage;

    @Data
    @Builder
    public static class RunCaseResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String runId;
        private String submissionTestId;
        private String testCaseId;
        private String caseLabel;
        private String status;
        private String runtime;
        private String memory;
        private Long runtimeMs;
        private Double memoryMb;
        private Long runtimeUs;
        private Long cpuMs;
        private String detail;
        private String output;
        private String expectedOutput;
        private List<InputParam> inputs;

        @Data
        @Builder
        public static class InputParam implements Serializable {
            private static final long serialVersionUID = 1L;

            private String id;
            private String label;
            private String name;
            private String value;
        }
    }
}

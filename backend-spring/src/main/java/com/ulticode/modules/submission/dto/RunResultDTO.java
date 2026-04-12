package com.ulticode.modules.submission.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RunResultDTO {

    private String id;
    private String problemId;
    private String userId;
    private String verdict;
    private String runtime;
    private String memory;
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

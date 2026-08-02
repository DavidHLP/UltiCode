package com.ulticode.modules.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RunSubmissionDTO {

    @NotBlank(message = "Language is required")
    private String language;

    @NotBlank(message = "Code cannot be empty")
    @Size(max = 65536, message = "Code must not exceed 65536 characters")
    private String code;

    private List<RunTestCase> testCases;

    @Data
    public static class RunTestCase {
        private String id;
        private String label;
        private String output;
        private List<RunInput> inputs;
    }

    @Data
    public static class RunInput {
        private String id;
        private String label;
        private String name;
        private String value;
        /**
         * Optional OJ data-type hint forwarded to the D-form harness.
         * The harness prefers {@code spec["type"]} over a Java annotation
         * or Python type hint on the Solution method's argument, which
         * matters for unannotated user code (e.g. raw
         * {@code class Solution: def reverse(self, head): ...} where the
         * backend can't infer that {@code head} should be a
         * {@code ListNode}).
         *
         * <p>Allowed values mirror the
         * {@code @ulticode/sandbox-types#OJDataType} union on the
         * frontend. Any value the harness doesn't recognize silently
         * falls back to the raw JSON literal.
         */
        private String type;
    }
}

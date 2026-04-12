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
    }
}

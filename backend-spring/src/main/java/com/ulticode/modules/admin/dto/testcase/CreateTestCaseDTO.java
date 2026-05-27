package com.ulticode.modules.admin.dto.testcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for creating a test case.
 */
@Data
public class CreateTestCaseDTO {

    @NotNull(message = "is_sample is required")
    private Boolean isSample;

    private Boolean isHidden;

    private Integer testOrder;

    @NotBlank(message = "input_text is required")
    private String inputText;

    @NotBlank(message = "output_text is required")
    private String outputText;

    private String explanation;

    private String constraints;

    private String inputs;
}

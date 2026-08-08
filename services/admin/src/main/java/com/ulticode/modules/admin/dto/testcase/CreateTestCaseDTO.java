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

    /**
     * Required: the wire contract models test-case author intent as one
     * "CaseScope" dimension — SAMPLE ({@code is_sample=true, is_hidden=false})
     * or HIDDEN ({@code is_sample=false, is_hidden=true}). Callers MUST send
     * both flags together so the persisted row satisfies the XOR invariant
     * the judging pipeline depends on; the service still validates the pair.
     */
    @NotNull(message = "is_hidden is required; send the (is_sample, is_hidden) pair that matches the desired SAMPLE or HIDDEN scope")
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

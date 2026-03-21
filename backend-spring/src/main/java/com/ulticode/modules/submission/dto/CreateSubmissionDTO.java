package com.ulticode.modules.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for creating a new submission.
 */
@Data
public class CreateSubmissionDTO {

    /**
     * Problem ID to submit for
     */
    @NotNull(message = "Problem ID is required")
    private Long problemId;

    /**
     * Programming language used (e.g., javascript, python, java)
     */
    @NotBlank(message = "Language is required")
    private String language;

    /**
     * Source code to submit
     */
    @NotBlank(message = "Code cannot be empty")
    private String code;
}

package com.ulticode.app.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for creating a new submission.
 */
@Data
public class CreateSubmissionDTO {

    @NotNull(message = "Problem ID is required")
    private Long problemId;

    @NotBlank(message = "Language is required")
    private String language;

    @NotBlank(message = "Code cannot be empty")
    private String code;
}

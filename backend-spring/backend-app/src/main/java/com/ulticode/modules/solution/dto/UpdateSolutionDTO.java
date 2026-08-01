package com.ulticode.modules.solution.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * DTO for updating an existing solution.
 */
@Data
@Schema(description = "Update solution request")
public class UpdateSolutionDTO {

    @NotBlank(message = "Title is required")
    @Schema(description = "Solution title", example = "My updated solution using dynamic programming")
    private String title;

    @NotBlank(message = "Content is required")
    @Schema(description = "Solution content in markdown", example = "## Updated Approach\n\nWe use DP to solve this...")
    private String content;

    @NotBlank(message = "Language is required")
    @Schema(description = "Programming language", example = "java")
    private String language;

    @Size(max = 20, message = "Tags must not exceed 20 entries")
    @Schema(description = "Tags for the solution", example = "[\"dynamic-programming\", \"array\"]")
    private List<String> tags;
}

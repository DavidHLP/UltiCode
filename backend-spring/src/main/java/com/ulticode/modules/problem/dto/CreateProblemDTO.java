package com.ulticode.modules.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * DTO for creating a new problem.
 */
@Data
@Schema(description = "Create problem request")
public class CreateProblemDTO {

    @NotBlank(message = "Slug is required")
    @Size(max = 120, message = "Slug must not exceed 120 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Schema(description = "URL-friendly identifier", example = "two-sum")
    private String slug;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Problem title", example = "Two Sum")
    private String title;

    @NotNull(message = "Difficulty is required")
    @Pattern(regexp = "^(Easy|Medium|Hard)$", message = "Difficulty must be Easy, Medium, or Hard")
    @Schema(description = "Difficulty level", example = "Easy", allowableValues = {"Easy", "Medium", "Hard"})
    private String difficulty;

    @Schema(description = "Whether this is a premium problem", example = "false")
    private Boolean isPremium;

    @Schema(description = "Whether the problem is published", example = "true")
    private Boolean isPublished;

    @Schema(description = "Problem summary")
    private String summary;

    @Schema(description = "Problem content (markdown)")
    private String content;

    @Schema(description = "Examples as JSON array", example = "[{\"input\":\"...\", \"output\":\"...\", \"explanation\":\"...\"}]")
    private String examples;

    @Schema(description = "Constraints")
    private String constraints;

    @Schema(description = "Hints as JSON array", example = "[\"hint1\", \"hint2\"]")
    private String hints;

    @Schema(description = "Supported languages as JSON array", example = "[\"javascript\", \"python\", \"java\", \"c\", \"cpp\"]")
    private List<String> languages;

    @Schema(description = "Tags as JSON array", example = "[\"array\", \"dynamic-programming\"]")
    private List<String> tags;
}

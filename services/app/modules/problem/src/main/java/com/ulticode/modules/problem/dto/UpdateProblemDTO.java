package com.ulticode.modules.problem.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * DTO for updating an existing problem.
 */
@Data
@Schema(description = "Update problem request")
public class UpdateProblemDTO {

    @Size(max = 120, message = "Slug must not exceed 120 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    @Schema(description = "URL-friendly identifier", example = "two-sum")
    private String slug;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Problem title", example = "Two Sum")
    private String title;

    @Pattern(regexp = "^(Easy|Medium|Hard)$", message = "Difficulty must be Easy, Medium, or Hard")
    @Schema(description = "Difficulty level", example = "Easy", allowableValues = {"Easy", "Medium", "Hard"})
    private String difficulty;

    @Schema(description = "Whether this is a premium problem", example = "false")
    private Boolean isPremium;

    @Schema(description = "Whether the problem is published", example = "true")
    private Boolean isPublished;

    @Schema(description = "Whether the problem has an official solution", example = "false")
    private Boolean hasSolution;

    @Schema(description = "Problem summary", example = "Given an array of integers...")
    private String summary;

    @Schema(description = "Problem full content (markdown)", example = "## Description\\nGiven...")
    private String content;

    @Schema(description = "Problem constraints as JSON array", example = "[\"1 <= nums.length <= 10^4\"]")
    private String constraintsJson;

    @Schema(description = "Problem hints as JSON array", example = "[\"Think about hash map\"]")
    private String hints;

    @Schema(description = "Examples as JSON array", example = "[{\"input\":\"...\", \"output\":\"...\", \"explanation\":\"...\"}]")
    private String examples;

    @Schema(description = "Supported languages with starter code", example = "[{\"language\":\"javascript\",\"starterCode\":\"function twoSum(nums, target) {\\n  // Your code here\\n}\"}]")
    @JsonDeserialize(contentAs = LanguageConfigDTO.class)
    private List<LanguageConfigDTO> languages;

    @Schema(description = "Tags as JSON array", example = "[\"array\", \"dynamic-programming\"]")
    private List<String> tags;
}

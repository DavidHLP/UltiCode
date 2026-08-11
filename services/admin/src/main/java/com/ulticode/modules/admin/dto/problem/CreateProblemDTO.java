package com.ulticode.modules.admin.dto.problem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Admin-owned create-problem request body, wire-compatible mirror of the
 * App module's {@code CreateProblemDTO}. The create write routes through the
 * {@code ProblemAdministrationService} cutover seam; this DTO keeps the HTTP
 * request surface unchanged without importing the App-private DTO.
 */
@Data
public class CreateProblemDTO {

    @NotBlank(message = "Slug is required")
    @Size(max = 120, message = "Slug must not exceed 120 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, hyphens")
    private String slug;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotNull(message = "Difficulty is required")
    @Pattern(regexp = "^(Easy|Medium|Hard)$", message = "Difficulty must be Easy, Medium, or Hard")
    private String difficulty;

    private Boolean isPremium;

    private Boolean isPublished;

    private String summary;

    private String content;

    private String examples;

    private String constraints;

    private String hints;

    private List<String> tags;
}

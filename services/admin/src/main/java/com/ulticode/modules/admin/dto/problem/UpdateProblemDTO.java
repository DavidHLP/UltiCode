package com.ulticode.modules.admin.dto.problem;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Admin-owned update-problem request body, wire-compatible mirror of the
 * App module's {@code UpdateProblemDTO}. The update write routes through the
 * {@code ProblemAdministrationService} cutover seam; this DTO keeps the HTTP
 * request surface unchanged without importing the App-private DTO.
 */
@Data
public class UpdateProblemDTO {

    @Size(max = 120, message = "Slug must not exceed 120 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, hyphens")
    private String slug;

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Pattern(regexp = "^(Easy|Medium|Hard)$", message = "Difficulty must be Easy, Medium, or Hard")
    private String difficulty;

    private Boolean isPremium;

    private Boolean isPublished;

    private Boolean hasSolution;

    private String summary;

    private String content;

    private String constraintsJson;

    private String hints;

    private String examples;

    private List<LanguageConfig> languages;

    private List<String> tags;

    /**
     * Admin-owned language-config shape ({@code language}/{@code starterCode}),
     * wire-compatible mirror of the App module's {@code LanguageConfigDTO}.
     */
    @Data
    public static class LanguageConfig {
        private String language;
        private String starterCode;
    }
}

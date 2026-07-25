package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Single problem item for import")
public class ImportProblemItemDTO {

    @NotBlank(message = "Slug is required")
    @Schema(description = "Problem slug")
    private String slug;

    @NotBlank(message = "Title is required")
    @Schema(description = "Problem title")
    private String title;

    @NotBlank(message = "Difficulty is required")
    @Schema(description = "Problem difficulty")
    private String difficulty;

    @Schema(description = "Problem status")
    private String status;

    @Schema(description = "Whether problem is premium")
    private Boolean isPremium;

    @Schema(description = "Whether problem is published")
    private Boolean isPublished;

    @Schema(description = "Problem summary")
    private String summary;

    @Schema(description = "Problem content")
    private String content;

    @Schema(description = "Problem constraints")
    private List<String> constraints;

    @Schema(description = "Problem hints")
    private List<String> hints;

    @Schema(description = "Problem examples")
    private List<Map<String, String>> examples;

    @Schema(description = "Problem tags")
    private List<String> tags;
}

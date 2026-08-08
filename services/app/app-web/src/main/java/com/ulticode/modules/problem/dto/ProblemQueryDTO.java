package com.ulticode.modules.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for querying problems with filters and pagination.
 */
@Data
@Schema(description = "Problem query parameters")
public class ProblemQueryDTO {

    @Min(value = 1, message = "page must be >= 1")
    @Schema(description = "Page number (1-based)", example = "1")
    private Integer page;

    @Min(value = 1, message = "pageSize must be >= 1")
    @Max(value = 100, message = "pageSize must be <= 100")
    @Schema(description = "Number of items per page", example = "20")
    private Integer pageSize;

    @Min(value = 1, message = "limit must be >= 1")
    @Max(value = 100, message = "limit must be <= 100")
    @Schema(description = "Number of items per page (alias for pageSize)", example = "20")
    private Integer limit;

    @Pattern(regexp = "^(?i)(Easy|Medium|Hard)$", message = "difficulty must be Easy/Medium/Hard")
    @Schema(description = "Filter by difficulty", example = "Easy", allowableValues = {"Easy", "Medium", "Hard", "EASY", "MEDIUM", "HARD"})
    private String difficulty;

    @Pattern(regexp = "^(solved|attempted|todo)$", message = "status must be solved/attempted/todo")
    @Schema(description = "Filter by status", example = "todo", allowableValues = {"solved", "attempted", "todo"})
    private String status;

    @Size(max = 100, message = "search length must be <= 100")
    @Schema(description = "Search by ID or title", example = "two sum")
    private String search;

    @Schema(description = "Sort by field", example = "created_at")
    private String sortBy;

    @Pattern(regexp = "^(?i)(asc|desc)$", message = "sortOrder must be asc/desc")
    @Schema(description = "Sort order", example = "desc", allowableValues = {"asc", "desc"})
    private String sortOrder;

    @Schema(description = "Filter by published status", example = "true")
    private Boolean isPublished;

    @Schema(description = "Filter by deleted status", example = "false")
    private Boolean isDeleted;

    @Size(max = 100, message = "tag length must be <= 100")
    @Schema(description = "Filter by tag", example = "array")
    private String tag;

    @Pattern(regexp = "^(?i)(DRAFT|PUBLISHED|ARCHIVED)$", message = "publishStatus must be DRAFT/PUBLISHED/ARCHIVED")
    @Schema(description = "Filter by publish status (DRAFT/PUBLISHED/ARCHIVED)", allowableValues = {"DRAFT", "PUBLISHED", "ARCHIVED"})
    private String publishStatus;

    @Size(max = 100, message = "category length must be <= 100")
    @Schema(description = "Filter by category", example = "algorithms")
    private String category;

    @Schema(description = "Filter by premium status", example = "false")
    private Boolean isPremium;

    /**
     * Normalize limit to pageSize for backward compatibility
     */
    public Integer getPageSize() {
        if (pageSize != null) return pageSize;
        return limit;
    }

    /**
     * Getter for isPublished to ensure Lombok compatibility
     */
    public Boolean getIsPublished() {
        return isPublished;
    }

    /**
     * Getter for isDeleted to ensure Lombok compatibility
     */
    public Boolean getIsDeleted() {
        return isDeleted;
    }
}

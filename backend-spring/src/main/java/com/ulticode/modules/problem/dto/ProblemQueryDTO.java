package com.ulticode.modules.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DTO for querying problems with filters and pagination.
 */
@Data
@Schema(description = "Problem query parameters")
public class ProblemQueryDTO {

    @Schema(description = "Page number (1-based)", example = "1")
    private Integer page;

    @Schema(description = "Number of items per page", example = "20")
    private Integer pageSize;

    @Schema(description = "Number of items per page (alias for pageSize)", example = "20")
    private Integer limit;

    @Schema(description = "Filter by difficulty", example = "Easy", allowableValues = {"Easy", "Medium", "Hard", "EASY", "MEDIUM", "HARD"})
    private String difficulty;

    @Schema(description = "Filter by status", example = "todo", allowableValues = {"solved", "attempted", "todo"})
    private String status;

    @Schema(description = "Search by ID or title", example = "two sum")
    private String search;

    @Schema(description = "Sort by field", example = "created_at")
    private String sortBy;

    @Schema(description = "Sort order", example = "desc", allowableValues = {"asc", "desc"})
    private String sortOrder;

    @Schema(description = "Filter by published status", example = "true")
    private Boolean isPublished;

    @Schema(description = "Filter by deleted status", example = "false")
    private Boolean isDeleted;

    @Schema(description = "Filter by tag", example = "array")
    private String tag;

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

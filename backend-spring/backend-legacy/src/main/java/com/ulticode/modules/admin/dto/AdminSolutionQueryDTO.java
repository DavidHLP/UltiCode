package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DTO for querying admin solutions.
 * Follows Java naming conventions with camelCase field names.
 */
@Data
@Schema(description = "Admin solution query parameters")
public class AdminSolutionQueryDTO {

    @Schema(description = "Search by title or content")
    private String search;

    @Schema(description = "Filter by problem ID")
    private Long problemId;

    @Schema(description = "Filter by user ID")
    private String userId;

    @Schema(description = "Filter by flagged status")
    private Boolean isFlagged;

    @Schema(description = "Filter by published status")
    private Boolean isPublished;

    @Schema(description = "Filter by deleted status")
    private Boolean isDeleted;

    @Schema(description = "Page number (1-based)", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "Items per page", defaultValue = "10")
    private Integer limit = 10;

    @Schema(description = "Sort by field", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Sort order", defaultValue = "desc")
    private String sortOrder = "desc";
}

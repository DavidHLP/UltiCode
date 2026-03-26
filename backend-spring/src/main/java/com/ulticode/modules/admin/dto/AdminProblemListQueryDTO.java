package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DTO for querying admin problem list.
 * Follows Java naming conventions with camelCase field names.
 */
@Data
@Schema(description = "Admin problem list query parameters")
public class AdminProblemListQueryDTO {

    @Schema(description = "Search by name or description")
    private String search;

    @Schema(description = "Filter by featured status")
    private Boolean isFeatured;

    @Schema(description = "Filter by public status")
    private Boolean isPublic;

    @Schema(description = "Page number (1-based)", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "Items per page", defaultValue = "10")
    private Integer limit = 10;

    @Schema(description = "Sort by field", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Sort order", defaultValue = "desc")
    private String sortOrder = "desc";
}

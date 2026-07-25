package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DTO for querying admin users list.
 * Follows Java naming conventions with camelCase field names.
 */
@Data
@Schema(description = "Admin user query parameters")
public class AdminUserQueryDTO {

    @Schema(description = "Search by username, email, or name")
    private String search;

    @Schema(description = "Filter by role")
    private String role;

    @Schema(description = "Filter by active status")
    private Boolean isActive;

    @Schema(description = "Filter by banned status")
    private Boolean isBanned;

    @Schema(description = "Page number (1-based)", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "Items per page", defaultValue = "10")
    private Integer limit = 10;

    @Schema(description = "Sort by field", defaultValue = "joinedAt")
    private String sortBy = "joinedAt";

    @Schema(description = "Sort order", defaultValue = "desc")
    private String sortOrder = "desc";
}

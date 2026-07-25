package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for querying admin submissions.
 * Follows Java naming conventions with camelCase field names.
 */
@Data
@Schema(description = "Admin submission query parameters")
public class AdminSubmissionQueryDTO {

    @Schema(description = "Search by username or problem title")
    private String search;

    @Schema(description = "Filter by user ID")
    private String userId;

    @Schema(description = "Filter by problem ID")
    private Long problemId;

    @Schema(description = "Filter by status (Pending, Accepted, Wrong Answer, etc.)")
    private String status;

    @Schema(description = "Filter by programming language")
    private String language;

    @Schema(description = "Filter by start date (submission created after)")
    private LocalDateTime startDate;

    @Schema(description = "Filter by end date (submission created before)")
    private LocalDateTime endDate;

    @Schema(description = "Page number (1-based)", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "Items per page", defaultValue = "10")
    private Integer limit = 10;

    @Schema(description = "Sort by field (createdAt, runtime, memory, status)", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Sort order (asc, desc)", defaultValue = "desc")
    private String sortOrder = "desc";
}

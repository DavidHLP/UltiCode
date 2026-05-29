package com.ulticode.modules.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DTO for querying contests with filters and pagination.
 */
@Data
@Schema(description = "Contest query parameters")
public class ContestQueryDTO {

    @Schema(description = "Page number (1-based)", example = "1")
    private Integer page;

    @Schema(description = "Number of items per page", example = "20")
    private Integer pageSize;

    @Schema(description = "Filter by status", example = "upcoming", allowableValues = {"upcoming", "running", "finished", "cancelled", "draft"})
    private String status;

    @Schema(description = "Filter by premium status", example = "true", allowableValues = {"true", "false"})
    private Boolean isPremium;

    @Schema(description = "Search by ID, title, or slug", example = "weekly contest")
    private String search;

    @Schema(description = "Sort field", example = "startTime", allowableValues = {"startTime", "endTime", "createdAt", "title"})
    private String sort;

    @Schema(description = "Sort direction", example = "asc", allowableValues = {"asc", "desc"})
    private String direction;

    @Schema(description = "Filter by contest type", example = "ICPC", allowableValues = {"ICPC", "IOI", "CUSTOM"})
    private String contestType;

    @Schema(description = "Filter by rated status", example = "true")
    private Boolean isRated;

    @Schema(description = "Filter by start time from (ISO datetime)")
    private String startDateFrom;

    @Schema(description = "Filter by start time to (ISO datetime)")
    private String startDateTo;
}

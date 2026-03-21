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

    @Schema(description = "Filter by difficulty", example = "Easy", allowableValues = {"Easy", "Medium", "Hard"})
    private String difficulty;

    @Schema(description = "Filter by status", example = "todo", allowableValues = {"solved", "attempted", "todo"})
    private String status;

    @Schema(description = "Search by ID or title", example = "two sum")
    private String search;
}

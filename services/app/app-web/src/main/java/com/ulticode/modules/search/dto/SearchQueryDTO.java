package com.ulticode.modules.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Search query DTO for full-text search.
 */
@Data
@Schema(description = "Search query parameters")
public class SearchQueryDTO {

    /**
     * Search query string.
     */
    @NotBlank(message = "Query is required")
    @Size(min = 1, max = 200, message = "Query must be between 1 and 200 characters")
    @Schema(description = "Search query string", example = "Two Sum", required = true)
    private String query;

    /**
     * Search index type (optional).
     * If not specified, searches all indices.
     */
    @Schema(description = "Search index type (PROBLEMS, USERS, POSTS, SOLUTIONS). If not specified, searches all indices.",
            example = "PROBLEMS")
    private SearchIndexType index;

    /**
     * Page number (1-based).
     */
    @NotNull(message = "Page is required")
    @Min(value = 1, message = "Page must be at least 1")
    @Schema(description = "Page number (1-based)", example = "1", defaultValue = "1")
    private Integer page = 1;

    /**
     * Number of results per page.
     */
    @NotNull(message = "Limit is required")
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit must be at most 100")
    @Schema(description = "Number of results per page", example = "20", defaultValue = "20")
    private Integer limit = 20;

    /**
     * Calculate offset for pagination.
     *
     * @return the offset value
     */
    public int getOffset() {
        long offset = ((long) page - 1) * limit;
        return (int) Math.min(offset, Integer.MAX_VALUE);
    }
}

package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Slim VO for solution list responses.
 * Excludes heavy fields (content, summary, tags, etc.) to reduce payload size.
 */
@Schema(description = "Admin solution list item view object")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSolutionListItemVO(
        @Schema(description = "Solution unique identifier") String id,
        @Schema(description = "Solution title") String title,
        @Schema(description = "Programming language for this solution") String language,
        @Schema(description = "Number of views") Integer views,
        @Schema(description = "Whether the solution is published") Boolean isPublished,
        @Schema(description = "Whether the solution is flagged for review") Boolean isFlagged,
        @Schema(description = "Soft delete flag") Boolean isDeleted,
        @Schema(description = "Record creation timestamp") LocalDateTime createdAt,
        @Schema(description = "Author information") AuthorInfo author,
        @Schema(description = "Problem information") ProblemInfo problem
) {

    @Schema(description = "Author information")
    public record AuthorInfo(
            @Schema(description = "Author user ID") String id,
            @Schema(description = "Author username") String username,
            @Schema(description = "Author display name") String name,
            @Schema(description = "Author email") String email
    ) {}

    @Schema(description = "Problem information")
    public record ProblemInfo(
            @Schema(description = "Problem ID") String id,
            @Schema(description = "Problem slug (URL-friendly identifier)") String slug,
            @Schema(description = "Problem title") String title,
            @Schema(description = "Problem difficulty (easy/medium/hard)") String difficulty
    ) {}
}

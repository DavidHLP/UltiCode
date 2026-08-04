package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin Solution View Object for API responses.
 * Contains all fields needed for the admin frontend including nested author and problem info.
 */
@Data
@Schema(description = "Admin solution view object")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminSolutionVO {

    @Schema(description = "Solution unique identifier")
    private String id;

    @Schema(description = "Problem ID this solution belongs to")
    private Long problemId;

    @Schema(description = "User ID who created this solution")
    private String userId;

    @Schema(description = "Solution title")
    private String title;

    @Schema(description = "Solution content (markdown)")
    private String content;

    @Schema(description = "Summary/excerpt of the solution")
    private String summary;

    @Schema(description = "Programming language for this solution")
    private String language;

    @Schema(description = "Tags associated with this solution (JSON array string)")
    private String tags;

    @Schema(description = "Number of views")
    private Integer views;

    @Schema(description = "Whether the solution is published")
    private Boolean isPublished;

    @Schema(description = "When the solution was published")
    private LocalDateTime publishedAt;

    @Schema(description = "User ID who published the solution")
    private String publishedBy;

    @Schema(description = "Whether the solution is flagged for review")
    private Boolean isFlagged;

    @Schema(description = "Reason for flagging")
    private String flaggedReason;

    @Schema(description = "When the solution was flagged")
    private LocalDateTime flaggedAt;

    @Schema(description = "Soft delete flag")
    private Boolean isDeleted;

    @Schema(description = "When the solution was deleted")
    private LocalDateTime deletedAt;

    @Schema(description = "User ID who deleted the solution")
    private String deletedBy;

    @Schema(description = "Record creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Record last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Author information")
    private AuthorInfo author;

    @Schema(description = "Problem information")
    private ProblemInfo problem;

    /**
     * Author information nested object.
     */
    @Data
    @Schema(description = "Author information")
    public static class AuthorInfo {
        @Schema(description = "Author user ID")
        private String id;

        @Schema(description = "Author username")
        private String username;

        @Schema(description = "Author display name")
        private String name;

        @Schema(description = "Author email")
        private String email;
    }

    /**
     * Problem information nested object.
     */
    @Data
    @Schema(description = "Problem information")
    public static class ProblemInfo {
        @Schema(description = "Problem ID")
        private String id;

        @Schema(description = "Problem slug (URL-friendly identifier)")
        private String slug;

        @Schema(description = "Problem title")
        private String title;

        @Schema(description = "Problem difficulty (easy/medium/hard)")
        private String difficulty;
    }
}

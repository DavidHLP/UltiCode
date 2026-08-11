package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-owned problem view object for the problem list / detail / flagged /
 * moderation read-back endpoints.
 *
 * <p>Wire-compatible mirror of the App module's {@code ProblemVO}: identical
 * field set, JSON names and null-skipping so the Admin HTTP surface is
 * unchanged now that the reads flow through the public
 * {@code ProblemAdminReadPort} instead of the App-private DTO.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemAdminVO {

    private Long id;

    private String slug;

    private String title;

    @JsonProperty("difficulty")
    private String difficulty;

    @JsonProperty("acceptance_rate")
    private BigDecimal acceptanceRate;

    private String status;

    @JsonProperty("is_premium")
    private Boolean isPremium;

    @JsonProperty("has_solution")
    private Boolean hasSolution;

    @JsonProperty("is_published")
    private Boolean isPublished;

    @JsonProperty("published_at")
    private LocalDateTime publishedAt;

    @JsonProperty("published_by")
    private String publishedBy;

    @JsonProperty("is_deleted")
    private Boolean isDeleted;

    @JsonProperty("deleted_at")
    private LocalDateTime deletedAt;

    @JsonProperty("is_flagged")
    private Boolean isFlagged;

    @JsonProperty("flag_reason")
    private String flagReason;

    @JsonProperty("flag_reported_by")
    private String flagReportedBy;

    @JsonProperty("flag_reported_at")
    private LocalDateTime flagReportedAt;

    @JsonProperty("flag_status")
    private String flagStatus;

    @JsonProperty("flag_reviewed_by")
    private String flagReviewedBy;

    @JsonProperty("flag_reviewed_at")
    private LocalDateTime flagReviewedAt;

    @JsonProperty("flag_notes")
    private String flagNotes;

    @JsonProperty("submission_count")
    private Long submissionCount;

    @JsonProperty("solution_count")
    private Long solutionCount;

    private List<ProblemTagVO> tags;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Inner class for tag information in problem list.
     */
    @Data
    public static class ProblemTagVO {
        private String id;
        private String label;
    }
}

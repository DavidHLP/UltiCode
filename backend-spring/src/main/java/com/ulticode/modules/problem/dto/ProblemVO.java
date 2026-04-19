package com.ulticode.modules.problem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ulticode.modules.problem.entity.Problem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Problem View Object for API responses.
 * Contains all fields needed for the frontend.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemVO {

    /**
     * Problem unique identifier
     */
    private Long id;

    /**
     * URL-friendly identifier for the problem
     */
    private String slug;

    /**
     * Problem title
     */
    private String title;

    /**
     * Difficulty level: EASY, MEDIUM, HARD
     */
    @JsonProperty("difficulty")
    private String difficulty;

    /**
     * Acceptance rate (0.00 to 100.00)
     */
    @JsonProperty("acceptance_rate")
    private BigDecimal acceptanceRate;

    /**
     * Problem status for current user: solved, attempted, todo
     */
    private String status;

    /**
     * Whether this is a premium-only problem
     */
    @JsonProperty("is_premium")
    private Boolean isPremium;

    /**
     * Whether the problem has an official solution
     */
    @JsonProperty("has_solution")
    private Boolean hasSolution;

    /**
     * Date when the problem was completed (by user)
     */
    @JsonProperty("completed_time")
    private LocalDateTime completedTime;

    /**
     * Whether the problem is published
     */
    @JsonProperty("is_published")
    private Boolean isPublished;

    /**
     * When the problem was published
     */
    @JsonProperty("published_at")
    private LocalDateTime publishedAt;

    /**
     * ID of user who published the problem
     */
    @JsonProperty("published_by")
    private String publishedBy;

    /**
     * Whether the problem is soft deleted
     */
    @JsonProperty("is_deleted")
    private Boolean isDeleted;

    /**
     * When the problem was deleted
     */
    @JsonProperty("deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Whether the problem is flagged for review
     */
    @JsonProperty("is_flagged")
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    @JsonProperty("flag_reason")
    private String flagReason;

    /**
     * ID of user who reported the flag
     */
    @JsonProperty("flag_reported_by")
    private String flagReportedBy;

    /**
     * When the flag was reported
     */
    @JsonProperty("flag_reported_at")
    private LocalDateTime flagReportedAt;

    /**
     * Flag status: PENDING, REVIEWED, RESOLVED, DISMISSED
     */
    @JsonProperty("flag_status")
    private String flagStatus;

    /**
     * ID of user who reviewed the flag
     */
    @JsonProperty("flag_reviewed_by")
    private String flagReviewedBy;

    /**
     * When the flag was reviewed
     */
    @JsonProperty("flag_reviewed_at")
    private LocalDateTime flagReviewedAt;

    /**
     * Notes from flag review
     */
    @JsonProperty("flag_notes")
    private String flagNotes;

    /**
     * Number of submissions for this problem
     */
    @JsonProperty("submission_count")
    private Long submissionCount;

    /**
     * Number of solutions for this problem
     */
    @JsonProperty("solution_count")
    private Long solutionCount;

    /**
     * Tags associated with this problem
     */
    private List<ProblemTagVO> tags;

    /**
     * Record creation timestamp
     */
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp
     */
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Create a ProblemVO from a Problem entity.
     *
     * @param problem the problem entity
     * @return the problem view object
     */
    public static ProblemVO from(Problem problem) {
        if (problem == null) {
            return null;
        }
        ProblemVO vo = new ProblemVO();
        vo.setId(problem.getId());
        vo.setSlug(problem.getSlug());
        vo.setTitle(problem.getTitle());
        vo.setDifficulty(problem.getDifficulty() != null ? problem.getDifficulty().toUpperCase() : null);
        vo.setAcceptanceRate(problem.getAcceptanceRate());
        vo.setStatus(problem.getStatus());
        vo.setIsPremium(problem.getIsPremium());
        vo.setHasSolution(problem.getHasSolution());
        vo.setIsPublished(problem.getIsPublished());
        vo.setPublishedAt(problem.getPublishedAt());
        vo.setPublishedBy(problem.getPublishedBy());
        vo.setIsDeleted(problem.getIsDeleted());
        vo.setDeletedAt(problem.getDeletedAt());
        vo.setIsFlagged(problem.getIsFlagged());
        vo.setFlagReason(problem.getFlagReason());
        vo.setFlagReportedBy(problem.getFlagReportedBy());
        vo.setFlagReportedAt(problem.getFlagReportedAt());
        vo.setFlagStatus(problem.getFlagStatus());
        vo.setFlagReviewedBy(problem.getFlagReviewedBy());
        vo.setFlagReviewedAt(problem.getFlagReviewedAt());
        vo.setFlagNotes(problem.getFlagNotes());
        vo.setCreatedAt(problem.getCreatedAt());
        vo.setUpdatedAt(problem.getUpdatedAt());
        vo.setSubmissionCount(0L);
        vo.setSolutionCount(0L);
        vo.setTags(List.of());
        return vo;
    }

    /**
     * Create a ProblemVO from a Problem entity with acceptance rate override.
     *
     * @param problem the problem entity
     * @param acceptanceRate the acceptance rate to set (overrides entity value)
     * @return the problem view object
     */
    public static ProblemVO from(Problem problem, BigDecimal acceptanceRate) {
        ProblemVO vo = from(problem);
        vo.setAcceptanceRate(acceptanceRate);
        return vo;
    }

    /**
     * Inner class for tag information in problem list
     */
    @Data
    public static class ProblemTagVO {
        private String id;
        private String label;
    }
}

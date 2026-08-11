package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity-free projection of a full {@code problems} row for administrative
 * consumers (list / detail / export / flag-moderation read-back).
 *
 * <p>Field set mirrors the App problem module's {@code ProblemVO} so the
 * Admin edge can rebuild its wire VO without importing the entity or the
 * module DTO. {@code tags} are populated only by batch list/export
 * reads; single-row reads leave them empty, matching the legacy
 * {@code ProblemVO.from(entity)} behaviour. {@code version} is the opaque
 * owner-side optimistic-concurrency token used by administrative writes.
 */
public record ProblemAdminRowDTO(
        Long id,
        String slug,
        String title,
        String difficulty,
        BigDecimal acceptanceRate,
        String status,
        Boolean isPremium,
        Boolean hasSolution,
        Boolean isPublished,
        LocalDateTime publishedAt,
        String publishedBy,
        Boolean isDeleted,
        LocalDateTime deletedAt,
        Boolean isFlagged,
        String flagReason,
        String flagReportedBy,
        LocalDateTime flagReportedAt,
        String flagStatus,
        String flagReviewedBy,
        LocalDateTime flagReviewedAt,
        String flagNotes,
        Long submissionCount,
        Long solutionCount,
        List<ProblemAdminTagDTO> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) implements Serializable {

    /**
     * Source-compatible constructor for read consumers that predate the
     * owner-side optimistic-lock token.
     */
    public ProblemAdminRowDTO(
            Long id,
            String slug,
            String title,
            String difficulty,
            BigDecimal acceptanceRate,
            String status,
            Boolean isPremium,
            Boolean hasSolution,
            Boolean isPublished,
            LocalDateTime publishedAt,
            String publishedBy,
            Boolean isDeleted,
            LocalDateTime deletedAt,
            Boolean isFlagged,
            String flagReason,
            String flagReportedBy,
            LocalDateTime flagReportedAt,
            String flagStatus,
            String flagReviewedBy,
            LocalDateTime flagReviewedAt,
            String flagNotes,
            Long submissionCount,
            Long solutionCount,
            List<ProblemAdminTagDTO> tags,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(id, slug, title, difficulty, acceptanceRate, status, isPremium, hasSolution,
                isPublished, publishedAt, publishedBy, isDeleted, deletedAt, isFlagged,
                flagReason, flagReportedBy, flagReportedAt, flagStatus, flagReviewedBy,
                flagReviewedAt, flagNotes, submissionCount, solutionCount, tags, createdAt,
                updatedAt, null);
    }

    public ProblemAdminRowDTO {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}

package com.ulticode.modules.problem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Admin problem detail response DTO.
 * Extends public DTO with all moderation and management fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailAdminVO extends ProblemDetailPublicVO {

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
}

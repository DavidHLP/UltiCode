package com.ulticode.modules.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for creating a new contest.
 */
@Data
@Schema(description = "Create contest request")
public class CreateContestDTO {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\p{P}]+$", message = "Title must contain only letters, numbers, spaces, and punctuation")
    @Schema(description = "Contest title", example = "Weekly Contest #123")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Schema(description = "Contest description", example = "This week's contest features dynamic programming problems.")
    private String description;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    @Schema(description = "Contest start time", example = "2024-12-31T10:00:00")
    private LocalDateTime startTime;

    @NotNull(message = "Duration is required")
    @Min(value = 5, message = "Duration must be at least 5 minutes")
    @Max(value = 1440, message = "Duration must not exceed 24 hours (1440 minutes)")
    @Schema(description = "Contest duration in minutes", example = "120")
    private Integer duration;

    @Min(value = 1, message = "Max participants must be at least 1")
    @Max(value = 10000, message = "Max participants must not exceed 10000")
    @Schema(description = "Maximum number of participants", example = "1000")
    private Integer maxParticipants;

    @Schema(description = "Whether this is a premium contest", example = "false")
    private Boolean isPremium;

    @Schema(description = "Whether to publish the contest", example = "true")
    private Boolean isPublished;

    @Schema(description = "List of problem IDs to include in the contest", example = "[1, 2, 3]")
    private List<Long> problemIds;

    /**
     * Scored problem attachments. Each entry pairs a problem id with the
     * author's chosen score. When present, the contest and every scored
     * {@code ContestProblem} are persisted in the same transaction so a
     * mid-list failure rolls back the whole contest (no partial persistence).
     * Preferred over {@link #problemIds}, which is retained for backward
     * compatibility and attaches each problem with the default score.
     */
    @Valid
    @Schema(description = "Scored problem attachments (problemId + score). Atomic with contest creation.")
    private List<AddContestProblemDTO> problems;

    @Schema(description = "List of tags for the contest", example = "[\"dp\", \"greedy\", \"array\"]")
    private List<String> tags;

    @Size(max = 255, message = "Slug must not exceed 255 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase letters, numbers, and hyphens")
    @Schema(description = "URL-friendly identifier for the contest", example = "weekly-contest-123")
    private String slug;

    @Pattern(regexp = "^(ICPC|IOI|CUSTOM)$", message = "Contest type must be ICPC, IOI, or CUSTOM")
    @Schema(description = "Contest type/format", example = "ICPC", allowableValues = {"ICPC", "IOI", "CUSTOM"})
    private String contestType;

    @Schema(description = "Associated scoring rule ID")
    private String scoringRuleId;
}
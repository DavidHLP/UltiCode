package com.ulticode.modules.problem.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Problem entity representing the problems table.
 * Maps to the existing database schema from NestJS application.
 */
@Data
@TableName("problems")
public class Problem {

    /**
     * Problem unique identifier
     */
    @TableId(type = IdType.AUTO)
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
     * Difficulty level: Easy, Medium, Hard
     */
    private String difficulty;

    /**
     * Acceptance rate (0.00 to 100.00)
     */
    @TableField("acceptance_rate")
    private BigDecimal acceptanceRate;

    /**
     * Problem status for user: solved, attempted, todo
     */
    private String status;

    /**
     * Whether this is a premium-only problem
     */
    @TableField("is_premium")
    private Boolean isPremium;

    /**
     * Whether the problem has an official solution
     */
    @TableField("has_solution")
    private Boolean hasSolution;

    /**
     * Date when the problem was completed (by user)
     */
    @TableField("completed_time")
    private LocalDateTime completedTime;

    /**
     * Whether the problem is published
     */
    @TableField("is_published")
    private Boolean isPublished;

    /**
     * When the problem was published
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * ID of user who published the problem
     */
    @TableField("published_by")
    private String publishedBy;

    /**
     * Soft delete flag
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    /**
     * When the problem was deleted
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /**
     * ID of user who deleted the problem
     */
    @TableField("deleted_by")
    private String deletedBy;

    /**
     * Whether the problem is flagged for review
     */
    @TableField("is_flagged")
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    @TableField("flag_reason")
    private String flagReason;

    /**
     * ID of user who reported the flag
     */
    @TableField("flag_reported_by")
    private String flagReportedBy;

    /**
     * When the flag was reported
     */
    @TableField("flag_reported_at")
    private LocalDateTime flagReportedAt;

    /**
     * Flag status: PENDING, REVIEWED, RESOLVED, DISMISSED
     */
    @TableField("flag_status")
    private String flagStatus;

    /**
     * ID of user who reviewed the flag
     */
    @TableField("flag_reviewed_by")
    private String flagReviewedBy;

    /**
     * When the flag was reviewed
     */
    @TableField("flag_reviewed_at")
    private LocalDateTime flagReviewedAt;

    /**
     * Notes from flag review
     */
    @TableField("flag_notes")
    private String flagNotes;

    /**
     * Record creation timestamp
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * Version number for optimistic locking
     */
    private Integer version;

    /**
     * Per-problem time limit in seconds (ADR-002 §8 / P2-1). NULL means
     * "use the global sandbox default". Lets hard problems allow more time
     * than easy ones. Maps to column {@code time_limit} via
     * {@code mapUnderscoreToCamelCase}.
     */
    private Integer timeLimit;

    /**
     * Per-problem memory limit in MiB (ADR-002 §8 / P2-1). NULL means
     * "use the global sandbox default". Maps to column {@code memory_limit}.
     */
    private Integer memoryLimit;
}

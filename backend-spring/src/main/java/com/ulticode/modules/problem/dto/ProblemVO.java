package com.ulticode.modules.problem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
     * Difficulty level: Easy, Medium, Hard
     */
    private String difficulty;

    /**
     * Acceptance rate (0.00 to 100.00)
     */
    private BigDecimal acceptanceRate;

    /**
     * Problem status for current user: solved, attempted, todo
     */
    private String status;

    /**
     * Whether this is a premium-only problem
     */
    private Boolean isPremium;

    /**
     * Whether the problem has an official solution
     */
    private Boolean hasSolution;

    /**
     * Date when the problem was completed (by user)
     */
    private LocalDateTime completedTime;

    /**
     * Whether the problem is published
     */
    private Boolean isPublished;

    /**
     * When the problem was published
     */
    private LocalDateTime publishedAt;

    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp
     */
    private LocalDateTime updatedAt;
}

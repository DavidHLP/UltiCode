package com.ulticode.recommend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a single recommended problem item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier of the problem.
     */
    private Long problemId;

    /**
     * URL-friendly slug for the problem.
     */
    private String slug;

    /**
     * Display title of the problem.
     */
    private String title;

    /**
     * Difficulty level (e.g., "Easy", "Medium", "Hard").
     */
    private String difficulty;

    /**
     * Recommendation score (0.0 to 1.0, higher is better).
     */
    private double score;

    /**
     * Tags associated with the problem.
     */
    private List<String> tags;

    /**
     * Human-readable reason for why this problem was recommended.
     */
    private String reason;
}

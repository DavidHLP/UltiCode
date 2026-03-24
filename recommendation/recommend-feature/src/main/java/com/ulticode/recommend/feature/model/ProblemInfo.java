package com.ulticode.recommend.feature.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Represents problem information for feature extraction purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemInfo {

    /**
     * The unique ID of the problem.
     */
    private Long problemId;

    /**
     * The difficulty level: "Easy", "Medium", or "Hard".
     */
    private String difficulty;

    /**
     * Set of tags associated with the problem (e.g., "array", "dynamic-programming").
     */
    private Set<String> tags;

    /**
     * The acceptance rate of the problem (0-1).
     */
    private Double acceptanceRate;

    /**
     * The title of the problem (optional).
     */
    private String title;

    /**
     * The slug/identifier of the problem (optional).
     */
    private String slug;

    /**
     * When the problem was created (optional).
     */
    private LocalDateTime createdAt;

    /**
     * Number of submissions for this problem (optional).
     */
    private Integer submissionCount;
}

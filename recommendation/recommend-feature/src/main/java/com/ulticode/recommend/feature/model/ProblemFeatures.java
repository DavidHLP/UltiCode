package com.ulticode.recommend.feature.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Represents extracted features for a programming problem.
 *
 * <p>This model contains various features used for problem recommendations:
 * <ul>
 *   <li>Difficulty features - normalized difficulty score</li>
 *   <li>Tag features - tags, categories, and weights</li>
 *   <li>Quality features - acceptance rate and submission statistics</li>
 *   <li>Popularity features - likes and dislikes</li>
 *   <li>Similarity features - related problems</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemFeatures {

    /**
     * The unique ID of the problem.
     */
    private Long problemId;

    /**
     * The slug/identifier of the problem.
     */
    private String slug;

    /**
     * The title of the problem.
     */
    private String title;

    // ==================== Difficulty Features ====================

    /**
     * The difficulty level: "Easy", "Medium", or "Hard".
     */
    private String difficulty;

    /**
     * Difficulty score normalized to 0-1.
     * Easy = 0.2, Medium = 0.5, Hard = 0.8.
     */
    private double difficultyScore;

    // ==================== Tag Features ====================

    /**
     * Set of tags associated with the problem (e.g., "array", "dynamic-programming").
     */
    private Set<String> tags;

    /**
     * High-level categories derived from tags.
     * Examples: "algorithm", "data-structure", "math", "string".
     */
    private Set<String> categories;

    /**
     * Weight for each tag based on rarity/importance.
     * Rarer tags have higher weights.
     */
    private Map<String, Double> tagWeights;

    // ==================== Quality Features ====================

    /**
     * The acceptance rate of the problem (0-1).
     */
    private double acceptanceRate;

    /**
     * Total number of submissions for this problem.
     */
    private int totalSubmissions;

    /**
     * Number of accepted submissions for this problem.
     */
    private int acceptedSubmissions;

    /**
     * Combined quality metric based on acceptance rate and submission volume.
     * Normalized to 0-1.
     */
    private double qualityScore;

    // ==================== Popularity Features ====================

    /**
     * Number of likes/upvotes for this problem.
     */
    private int likes;

    /**
     * Number of dislikes/downvotes for this problem.
     */
    private int dislikes;

    /**
     * Popularity score based on likes/dislikes ratio.
     * likes / (likes + dislikes), default 0.5 if no engagement.
     */
    private double popularityScore;

    // ==================== Similarity Features ====================

    /**
     * Average similarity to other problems in the corpus.
     */
    private double avgSimilarity;

    /**
     * IDs of similar problems.
     */
    private Set<Long> similarProblems;
}

package com.ulticode.recommend.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Represents a recommended problem with scoring details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendItem implements Comparable<RecommendItem> {

    private Long problemId;
    private String slug;
    private String title;
    private String difficulty;  // Easy, Medium, Hard
    private double score;
    private Set<String> tags;
    private String reason;
    private LocalDateTime createdAt;

    // Score components
    private double difficultyMatchScore;
    private double tagMatchScore;
    private double freshnessScore;
    private double qualityScore;

    // Weights
    private static final double WEIGHT_DIFFICULTY = 0.35;
    private static final double WEIGHT_TAG = 0.30;
    private static final double WEIGHT_FRESHNESS = 0.15;
    private static final double WEIGHT_QUALITY = 0.20;

    /**
     * Calculates the final recommendation score based on weighted components.
     *
     * @return the calculated final score
     */
    public double calculateFinalScore() {
        return WEIGHT_DIFFICULTY * difficultyMatchScore
             + WEIGHT_TAG * tagMatchScore
             + WEIGHT_FRESHNESS * freshnessScore
             + WEIGHT_QUALITY * qualityScore;
    }

    /**
     * Compares this item to another by score in descending order.
     *
     * @param other the other recommend item to compare to
     * @return negative if this item has higher score, positive if lower
     */
    @Override
    public int compareTo(RecommendItem other) {
        return Double.compare(other.score, this.score); // Descending
    }
}

package com.ulticode.recommend.feature.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Represents extracted features for a user.
 *
 * <p>This model contains various features used for personalized recommendations:
 * <ul>
 *   <li>Activity features - submission frequency and patterns</li>
 *   <li>Skill features - success rates by difficulty</li>
 *   <li>Tag features - preferences and mastery levels</li>
 *   <li>Learning features - velocity, streaks, consistency</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeatures {

    /**
     * The user's unique identifier.
     */
    private String userId;

    // ==================== Activity Features ====================

    /**
     * Activity level normalized to 0-1 based on submission frequency.
     * 0 = inactive, 1 = very active.
     */
    private double activityLevel;

    /**
     * Total number of submissions made by the user.
     */
    private int totalSubmissions;

    /**
     * Number of submissions in the last 7 days.
     */
    private int recentSubmissions;

    // ==================== Skill Features ====================

    /**
     * Success rate on Easy problems (0-1).
     */
    private double easySuccessRate;

    /**
     * Success rate on Medium problems (0-1).
     */
    private double mediumSuccessRate;

    /**
     * Success rate on Hard problems (0-1).
     */
    private double hardSuccessRate;

    /**
     * Overall skill level: "beginner", "intermediate", or "advanced".
     */
    private String skillLevel;

    // ==================== Tag Features ====================

    /**
     * Tag preferences based on problem count.
     * Value is the fraction of attempted problems containing each tag (0-1).
     */
    private Map<String, Double> tagPreferences;

    /**
     * Tag mastery levels based on success rates.
     * Value is the ratio of accepted to attempted for each tag (0-1).
     */
    private Map<String, Double> tagMastery;

    /**
     * Tags where mastery > 0.7 (user performs well).
     */
    private Set<String> strongTags;

    /**
     * Tags where mastery < 0.3 (user struggles).
     */
    private Set<String> weakTags;

    // ==================== Learning Features ====================

    /**
     * Learning velocity - rate of improvement over time (0+).
     * Higher values indicate faster improvement.
     */
    private double learningVelocity;

    /**
     * Number of consecutive days with at least one submission.
     */
    private int streakDays;

    /**
     * Consistency score normalized to 0-1.
     * Measures how regular the user's submission pattern is.
     */
    private double consistency;

    /**
     * Returns the success rate for a given difficulty.
     *
     * @param difficulty the difficulty level ("Easy", "Medium", "Hard")
     * @return the success rate, or 0.0 if unknown difficulty
     */
    public double getSuccessRate(String difficulty) {
        if (difficulty == null) {
            return 0.0;
        }
        return switch (difficulty) {
            case "Easy" -> easySuccessRate;
            case "Medium" -> mediumSuccessRate;
            case "Hard" -> hardSuccessRate;
            default -> 0.0;
        };
    }
}

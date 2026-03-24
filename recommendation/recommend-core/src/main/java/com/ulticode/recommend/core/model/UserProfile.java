package com.ulticode.recommend.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a user's profile for recommendation purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    private String userId;
    private int rating;
    private int maxRating;
    private String preferredLanguage;
    private Set<Long> solvedProblems;
    private Map<String, Double> tagMastery;  // tag -> mastery level (0-1)
    private Map<String, Integer> difficultyStats;  // difficulty -> count
    private String preferredDifficulty;
    private int totalSolved;
    private int totalAttempts;

    /**
     * Returns the set of tags where the user's mastery is below the given threshold.
     *
     * @param threshold the mastery threshold (0-1)
     * @return set of weak tags, or empty set if tagMastery is null
     */
    public Set<String> getWeakTags(double threshold) {
        if (tagMastery == null) return Set.of();
        return tagMastery.entrySet().stream()
            .filter(e -> e.getValue() < threshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Determines the appropriate difficulty level based on user's rating.
     *
     * @return "Easy" for rating &lt; 1200, "Medium" for 1200-1799, "Hard" for 1800+
     */
    public String getAppropriateDifficulty() {
        if (rating < 1200) return "Easy";
        else if (rating < 1800) return "Medium";
        else return "Hard";
    }

    /**
     * Checks if the user has solved a specific problem.
     *
     * @param problemId the problem ID to check
     * @return true if solved, false otherwise
     */
    public boolean hasSolved(Long problemId) {
        return solvedProblems != null && solvedProblems.contains(problemId);
    }
}

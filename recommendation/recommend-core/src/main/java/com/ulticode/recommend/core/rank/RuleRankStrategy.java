package com.ulticode.recommend.core.rank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule-based ranking strategy that scores items using multiple factors.
 *
 * <p>Scoring formula:
 * <pre>
 * score = 0.35 * difficultyMatch + 0.30 * tagMatch + 0.15 * freshness + 0.20 * quality
 * </pre>
 *
 * <p>Factors:
 * <ul>
 *   <li><b>difficultyMatch</b> (0.35): How well the problem difficulty matches user's rating level</li>
 *   <li><b>tagMatch</b> (0.30): How well the problem tags match user's tag mastery</li>
 *   <li><b>freshness</b> (0.15): How recently the problem was created</li>
 *   <li><b>quality</b> (0.20): Problem quality score (pass rate, likes, etc.)</li>
 * </ul>
 */
public class RuleRankStrategy implements RankStrategy {

    // Factor weights
    private static final double WEIGHT_DIFFICULTY = 0.35;
    private static final double WEIGHT_TAG = 0.30;
    private static final double WEIGHT_FRESHNESS = 0.15;
    private static final double WEIGHT_QUALITY = 0.20;

    // Freshness thresholds (in days)
    private static final long FRESHNESS_VERY_FRESH = 7;
    private static final long FRESHNESS_FRESH = 30;
    private static final long FRESHNESS_MODERATE = 90;
    private static final long FRESHNESS_SOMEWHAT_OLD = 365;

    @Override
    public List<RecommendItem> rank(List<RecommendItem> items,
                                    RecommendContext context,
                                    UserProfile profile) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
            .map(item -> calculateAndUpdateScore(item, profile))
            .sorted(Comparator.comparingDouble(RecommendItem::getScore).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Calculates and updates all score components for an item.
     *
     * @param item the item to score
     * @param profile the user profile
     * @return a new RecommendItem with updated scores (immutability)
     */
    private RecommendItem calculateAndUpdateScore(RecommendItem item, UserProfile profile) {
        double difficultyMatch = calculateDifficultyMatch(item, profile);
        double tagMatch = calculateTagMatch(item, profile);
        double freshness = calculateFreshness(item);
        double quality = item.getQualityScore();

        double finalScore = WEIGHT_DIFFICULTY * difficultyMatch
                          + WEIGHT_TAG * tagMatch
                          + WEIGHT_FRESHNESS * freshness
                          + WEIGHT_QUALITY * quality;

        return RecommendItem.builder()
            .problemId(item.getProblemId())
            .slug(item.getSlug())
            .title(item.getTitle())
            .difficulty(item.getDifficulty())
            .score(finalScore)
            .tags(item.getTags())
            .reason(item.getReason())
            .createdAt(item.getCreatedAt())
            .difficultyMatchScore(difficultyMatch)
            .tagMatchScore(tagMatch)
            .freshnessScore(freshness)
            .qualityScore(quality)
            .build();
    }

    /**
     * Calculates difficulty match score based on user rating.
     *
     * <p>Rating thresholds:
     * <ul>
     *   <li>rating &lt; 1200: Easy</li>
     *   <li>1200 &lt;= rating &lt; 1800: Medium</li>
     *   <li>rating &gt;= 1800: Hard</li>
     * </ul>
     *
     * @param item the problem item
     * @param profile the user profile
     * @return 1.0 for exact match, 0.5 for adjacent, 0.0 otherwise
     */
    private double calculateDifficultyMatch(RecommendItem item, UserProfile profile) {
        if (item == null || item.getDifficulty() == null) {
            return 0.5; // Neutral score
        }

        int userRating = (profile != null) ? profile.getRating() : 1500;
        String appropriateDifficulty = getDifficultyByRating(userRating);
        String problemDifficulty = item.getDifficulty();

        if (problemDifficulty.equals(appropriateDifficulty)) {
            return 1.0;
        }

        if (isAdjacentDifficulty(problemDifficulty, appropriateDifficulty)) {
            return 0.5;
        }

        return 0.0;
    }

    /**
     * Determines appropriate difficulty based on user rating.
     */
    private String getDifficultyByRating(int rating) {
        if (rating < 1200) return "Easy";
        if (rating < 1800) return "Medium";
        return "Hard";
    }

    /**
     * Checks if two difficulties are adjacent.
     *
     * <p>Easy and Hard are NOT adjacent.
     */
    private boolean isAdjacentDifficulty(String difficulty1, String difficulty2) {
        if (difficulty1 == null || difficulty2 == null) {
            return false;
        }

        // Easy-Medium or Medium-Hard are adjacent
        // Easy-Hard are NOT adjacent
        if (("Easy".equals(difficulty1) && "Medium".equals(difficulty2)) ||
            ("Medium".equals(difficulty1) && "Easy".equals(difficulty2))) {
            return true;
        }
        if (("Medium".equals(difficulty1) && "Hard".equals(difficulty2)) ||
            ("Hard".equals(difficulty1) && "Medium".equals(difficulty2))) {
            return true;
        }
        return false;
    }

    /**
     * Calculates tag match score based on user's tag mastery.
     *
     * <p>Formula: avgMastery * matchRatio
     * <ul>
     *   <li>avgMastery = average mastery level of matched tags</li>
     *   <li>matchRatio = matched tags / total problem tags</li>
     * </ul>
     *
     * @param item the problem item
     * @param profile the user profile
     * @return tag match score (0.0 - 1.0), or 0.5 for neutral cases
     */
    private double calculateTagMatch(RecommendItem item, UserProfile profile) {
        Set<String> problemTags = (item != null) ? item.getTags() : null;
        Map<String, Double> userTagMastery = (profile != null) ? profile.getTagMastery() : null;

        if (problemTags == null || problemTags.isEmpty() ||
            userTagMastery == null || userTagMastery.isEmpty()) {
            return 0.5; // Neutral score when no data
        }

        double totalMastery = 0.0;
        int matchedCount = 0;

        for (String tag : problemTags) {
            if (userTagMastery.containsKey(tag)) {
                totalMastery += userTagMastery.get(tag);
                matchedCount++;
            }
        }

        if (matchedCount == 0) {
            return 0.0;
        }

        double avgMastery = totalMastery / matchedCount;
        double matchRatio = (double) matchedCount / problemTags.size();

        return avgMastery * matchRatio;
    }

    /**
     * Calculates freshness score based on problem creation date.
     *
     * <p>Freshness levels:
     * <ul>
     *   <li>0-7 days: 1.0 (very fresh)</li>
     *   <li>8-30 days: 0.8</li>
     *   <li>31-90 days: 0.6</li>
     *   <li>91-365 days: 0.4</li>
     *   <li>&gt;365 days: 0.2 (stale)</li>
     * </ul>
     *
     * @param item the problem item
     * @return freshness score (0.2 - 1.0), or 0.5 if createdAt is null
     */
    private double calculateFreshness(RecommendItem item) {
        if (item == null || item.getCreatedAt() == null) {
            return 0.5;
        }

        long daysSinceCreation = ChronoUnit.DAYS.between(item.getCreatedAt(), LocalDateTime.now());

        if (daysSinceCreation <= FRESHNESS_VERY_FRESH) return 1.0;
        if (daysSinceCreation <= FRESHNESS_FRESH) return 0.8;
        if (daysSinceCreation <= FRESHNESS_MODERATE) return 0.6;
        if (daysSinceCreation <= FRESHNESS_SOMEWHAT_OLD) return 0.4;
        return 0.2;
    }

    @Override
    public int getPriority() {
        return 100; // High priority for rule-based ranking
    }
}

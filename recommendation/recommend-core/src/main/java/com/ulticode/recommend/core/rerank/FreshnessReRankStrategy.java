package com.ulticode.recommend.core.rerank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Re-ranking strategy that boosts items based on tag freshness.
 *
 * <p>This strategy analyzes user's tag mastery from their profile and
 * boosts items that contain tags the user hasn't practiced much.
 * The boost factor is inversely proportional to the mastery level,
 * meaning lower mastery results in higher boost.
 *
 * <p>For items with multiple tags, the minimum mastery across all tags
 * is used to calculate the boost, ensuring items with at least one
 * weak tag get significant boost.
 */
public class FreshnessReRankStrategy implements ReRankStrategy {

    private static final int DEFAULT_PRIORITY = 40;
    private static final double BOOST_MULTIPLIER = 0.5;

    @Override
    public List<RecommendItem> rerank(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    ) {
        // Handle null or empty input
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // If no profile or no tag mastery data, return items unchanged
        if (profile == null || profile.getTagMastery() == null) {
            return items.stream()
                    .map(this::copyItem)
                    .collect(Collectors.toList());
        }

        Map<String, Double> tagMastery = profile.getTagMastery();

        // Apply boost to each item based on tag freshness
        return items.stream()
                .map(item -> boostItem(item, tagMastery))
                .collect(Collectors.toList());
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * Boosts an item's score based on tag mastery.
     *
     * <p>Items without tags are not boosted.
     * For items with tags, the minimum mastery is used to calculate boost.
     *
     * @param item        the item to boost
     * @param tagMastery  map of tag to mastery level (0-1)
     * @return a new RecommendItem with boosted score
     */
    private RecommendItem boostItem(RecommendItem item, Map<String, Double> tagMastery) {
        Set<String> tags = item.getTags();

        // No boost for items without tags
        if (tags == null || tags.isEmpty()) {
            return copyItem(item);
        }

        // Calculate minimum mastery for all tags
        double minMastery = calculateMinMastery(tags, tagMastery);

        // Calculate boost factor: higher boost for lower mastery
        double boostFactor = 1.0 + (1.0 - minMastery) * BOOST_MULTIPLIER;

        // Apply boost to score
        double boostedScore = item.getScore() * boostFactor;

        return copyItemWithScore(item, boostedScore);
    }

    /**
     * Calculates the minimum mastery level across all tags.
     *
     * <p>Tags not in the mastery map are treated as mastery = 0 (full boost).
     *
     * @param tags        the set of tags to check
     * @param tagMastery  map of tag to mastery level
     * @return the minimum mastery level (0-1)
     */
    private double calculateMinMastery(Set<String> tags, Map<String, Double> tagMastery) {
        double minMastery = 1.0;

        for (String tag : tags) {
            Double mastery = tagMastery.get(tag);
            // If tag not in mastery map, treat as mastery = 0 (user hasn't practiced this tag)
            double tagMasteryValue = (mastery != null) ? mastery : 0.0;
            minMastery = Math.min(minMastery, tagMasteryValue);
        }

        return minMastery;
    }

    /**
     * Creates a copy of the item with the same values.
     *
     * @param item the item to copy
     * @return a new RecommendItem with the same values
     */
    private RecommendItem copyItem(RecommendItem item) {
        return RecommendItem.builder()
                .problemId(item.getProblemId())
                .slug(item.getSlug())
                .title(item.getTitle())
                .difficulty(item.getDifficulty())
                .score(item.getScore())
                .tags(item.getTags())
                .reason(item.getReason())
                .createdAt(item.getCreatedAt())
                .difficultyMatchScore(item.getDifficultyMatchScore())
                .tagMatchScore(item.getTagMatchScore())
                .freshnessScore(item.getFreshnessScore())
                .qualityScore(item.getQualityScore())
                .build();
    }

    /**
     * Creates a copy of the item with a new score.
     *
     * @param item      the item to copy
     * @param newScore  the new score to set
     * @return a new RecommendItem with the new score
     */
    private RecommendItem copyItemWithScore(RecommendItem item, double newScore) {
        return RecommendItem.builder()
                .problemId(item.getProblemId())
                .slug(item.getSlug())
                .title(item.getTitle())
                .difficulty(item.getDifficulty())
                .score(newScore)
                .tags(item.getTags())
                .reason(item.getReason())
                .createdAt(item.getCreatedAt())
                .difficultyMatchScore(item.getDifficultyMatchScore())
                .tagMatchScore(item.getTagMatchScore())
                .freshnessScore(item.getFreshnessScore())
                .qualityScore(item.getQualityScore())
                .build();
    }
}

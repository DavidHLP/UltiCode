package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Content-based recall strategy that recommends problems based on tag similarity.
 *
 * <p>This strategy analyzes the user's tag mastery history and recommends problems
 * with similar tags. The similarity is calculated using Jaccard similarity with
 * weighted mastery levels.
 *
 * <p>The algorithm works as follows:
 * <ol>
 *   <li>Extract user's tag preferences from profile.tagMastery</li>
 *   <li>Filter out problems the user has already solved</li>
 *   <li>Calculate tag similarity score for each remaining problem</li>
 *   <li>Sort problems by similarity score in descending order</li>
 *   <li>Return top N problems based on context size</li>
 * </ol>
 *
 * <p>Similarity calculation uses weighted Jaccard similarity:
 * <ul>
 *   <li>For each matching tag, add the mastery weight to the score</li>
 *   <li>Divide by the total number of unique tags (problem + user tags)</li>
 *   <li>This gives higher weight to tags the user has mastered more</li>
 * </ul>
 */
public class ContentRecallStrategy implements RecallStrategy {

    private static final int PRIORITY = 20;
    private static final int DEFAULT_SIZE = 10;

    /**
     * Available problems for recall.
     * In production, this would be fetched from database.
     */
    private final List<RecommendItem> availableProblems;

    /**
     * Creates a new ContentRecallStrategy with the given available problems.
     *
     * @param availableProblems the list of problems to filter and rank
     */
    public ContentRecallStrategy(List<RecommendItem> availableProblems) {
        this.availableProblems = availableProblems != null ? availableProblems : List.of();
    }

    @Override
    public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
        if (availableProblems.isEmpty()) {
            return List.of();
        }

        int requestedSize = context != null ? context.getSize() : DEFAULT_SIZE;
        Map<String, Double> userTagMastery = profile != null && profile.getTagMastery() != null
                ? profile.getTagMastery()
                : Map.of();
        Set<Long> solvedProblems = profile != null && profile.getSolvedProblems() != null
                ? profile.getSolvedProblems()
                : Set.of();

        return availableProblems.stream()
                // Filter out solved problems
                .filter(item -> !solvedProblems.contains(item.getProblemId()))
                // Calculate and assign similarity score
                .map(item -> calculateAndAssignScore(item, userTagMastery))
                // Sort by tag similarity score descending
                .sorted(Comparator.comparingDouble(RecommendItem::getTagMatchScore).reversed())
                // Limit to requested size
                .limit(requestedSize)
                .collect(Collectors.toList());
    }

    /**
     * Calculates the tag similarity score and assigns it to the item.
     *
     * <p>Uses weighted Jaccard similarity:
     * <ul>
     *   <li>Sum of mastery weights for matching tags</li>
     *   <li>Divided by total unique tags in both problem and user profile</li>
     * </ul>
     *
     * @param item the problem to score
     * @param userTagMastery the user's tag mastery map
     * @return a new RecommendItem with tagMatchScore set
     */
    private RecommendItem calculateAndAssignScore(RecommendItem item, Map<String, Double> userTagMastery) {
        double similarity = calculateTagSimilarity(item.getTags(), userTagMastery);

        return RecommendItem.builder()
                .problemId(item.getProblemId())
                .slug(item.getSlug())
                .title(item.getTitle())
                .difficulty(item.getDifficulty())
                .tags(item.getTags())
                .reason(item.getReason())
                .score(item.getScore())
                .difficultyMatchScore(item.getDifficultyMatchScore())
                .tagMatchScore(similarity)
                .freshnessScore(item.getFreshnessScore())
                .qualityScore(item.getQualityScore())
                .build();
    }

    /**
     * Calculates the tag similarity between problem tags and user's tag mastery.
     *
     * <p>Uses weighted Jaccard similarity formula:
     * <pre>
     *   similarity = sum(mastery[tag] for tag in intersection) / |union|
     * </pre>
     *
     * <p>Where:
     * <ul>
     *   <li>intersection = tags that exist in both problem and user mastery</li>
     *   <li>union = all unique tags from problem and user mastery</li>
     * </ul>
     *
     * @param problemTags the tags of the problem (may be null or empty)
     * @param userTagMastery the user's tag mastery map (may be null or empty)
     * @return similarity score between 0 and 1
     */
    private double calculateTagSimilarity(Set<String> problemTags, Map<String, Double> userTagMastery) {
        // Handle edge cases
        if (problemTags == null || problemTags.isEmpty()) {
            return 0.0;
        }
        if (userTagMastery == null || userTagMastery.isEmpty()) {
            return 0.0;
        }

        Set<String> problemTagSet = new HashSet<>(problemTags);
        Set<String> userTagSet = userTagMastery.keySet();

        // Calculate intersection (matching tags)
        Set<String> intersection = new HashSet<>(problemTagSet);
        intersection.retainAll(userTagSet);

        // If no matching tags, similarity is 0
        if (intersection.isEmpty()) {
            return 0.0;
        }

        // Calculate weighted sum for matching tags
        double weightedSum = 0.0;
        for (String tag : intersection) {
            weightedSum += userTagMastery.getOrDefault(tag, 0.0);
        }

        // Calculate union size
        Set<String> union = new HashSet<>(problemTagSet);
        union.addAll(userTagSet);

        // Weighted Jaccard similarity
        return weightedSum / union.size();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}

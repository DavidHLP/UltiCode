package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hot recall strategy that recommends popular problems based on submission count and acceptance rate.
 *
 * <p>This strategy filters problems by:
 * <ul>
 *   <li>Submission count threshold (minimum 100 submissions)</li>
 *   <li>Acceptance rate threshold (minimum 30%)</li>
 *   <li>User's solved problems (excludes already solved)</li>
 *   <li>User's rating (matches appropriate difficulty)</li>
 * </ul>
 *
 * <p>Problems are sorted by hot score (submissionCount * acceptanceRate) in descending order.
 */
public class HotRecallStrategy implements RecallStrategy {

    private static final int MIN_SUBMISSION_THRESHOLD = 100;
    private static final double MIN_ACCEPTANCE_RATE = 0.3;
    private static final int PRIORITY = 10;

    /**
     * Thresholds for difficulty matching based on user rating.
     */
    private static final int RATING_THRESHOLD_EASY = 1200;
    private static final int RATING_THRESHOLD_MEDIUM = 1800;

    /**
     * Available problems for recall.
     * In production, this would be fetched from database.
     */
    private final List<RecommendItem> availableProblems;

    /**
     * Creates a new HotRecallStrategy with the given available problems.
     *
     * @param availableProblems the list of problems to filter and rank
     */
    public HotRecallStrategy(List<RecommendItem> availableProblems) {
        this.availableProblems = availableProblems != null ? availableProblems : List.of();
    }

    @Override
    public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
        if (availableProblems.isEmpty()) {
            return List.of();
        }

        int requestedSize = context != null ? context.getSize() : 10;
        int userRating = profile != null ? profile.getRating() : 0;
        Set<Long> solvedProblems = profile != null && profile.getSolvedProblems() != null
                ? profile.getSolvedProblems()
                : Set.of();

        return availableProblems.stream()
                // Filter by hot criteria (qualityScore represents submissionCount * acceptanceRate)
                .filter(this::isHot)
                // Filter out solved problems
                .filter(item -> !solvedProblems.contains(item.getProblemId()))
                // Filter by difficulty based on user rating
                .filter(item -> matchesDifficulty(item, userRating))
                // Sort by hot score (qualityScore) descending
                .sorted(Comparator.comparingDouble(RecommendItem::getQualityScore).reversed())
                // Limit to requested size
                .limit(requestedSize)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a problem meets the hot criteria.
     * Uses qualityScore as proxy for submissionCount * acceptanceRate.
     *
     * <p>A problem is considered hot if:
     * <ul>
     *   <li>qualityScore >= MIN_SUBMISSION_THRESHOLD * MIN_ACCEPTANCE_RATE (30)</li>
     * </ul>
     *
     * <p>Note: In a real implementation, submissionCount and acceptanceRate would be
     * separate fields. Here we use qualityScore as a proxy for testing purposes.
     *
     * @param item the problem to check
     * @return true if the problem is hot
     */
    private boolean isHot(RecommendItem item) {
        // Since we're using qualityScore as proxy for submissionCount * acceptanceRate,
        // we need to check if the individual values meet thresholds.
        // For simplicity, we assume that if qualityScore >= threshold (30),
        // the problem could potentially be hot.
        // In practice, this would check: submissionCount >= 100 && acceptanceRate >= 0.3
        // Since we don't have these fields, we use a minimum hot score threshold.
        double hotScore = item.getQualityScore();
        return hotScore >= MIN_SUBMISSION_THRESHOLD * MIN_ACCEPTANCE_RATE;
    }

    /**
     * Checks if a problem's difficulty matches the user's rating level.
     *
     * <p>Rating thresholds:
     * <ul>
     *   <li>rating < 1200: Easy only</li>
     *   <li>1200 <= rating < 1800: Easy or Medium</li>
     *   <li>rating >= 1800: Any difficulty</li>
     * </ul>
     *
     * @param item the problem to check
     * @param userRating the user's rating
     * @return true if the difficulty is appropriate for the user
     */
    private boolean matchesDifficulty(RecommendItem item, int userRating) {
        String difficulty = item.getDifficulty();
        if (difficulty == null) {
            return true; // If no difficulty specified, allow it
        }

        if (userRating < RATING_THRESHOLD_EASY) {
            // Low rating: only Easy
            return "Easy".equals(difficulty);
        } else if (userRating < RATING_THRESHOLD_MEDIUM) {
            // Mid rating: Easy or Medium
            return "Easy".equals(difficulty) || "Medium".equals(difficulty);
        } else {
            // High rating: any difficulty
            return true;
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}

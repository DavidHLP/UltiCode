package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collaborative Filtering recall strategy based on User-Based CF.
 *
 * <p>This strategy recommends problems that similar users have solved but the current user has not.
 * User similarity is calculated using cosine similarity based on the overlap of solved problems.
 *
 * <p>Algorithm flow:
 * <ol>
 *   <li>Get the current user's solved problems</li>
 *   <li>Calculate similarity with all other users</li>
 *   <li>Select top K most similar users</li>
 *   <li>Collect problems solved by similar users but not by current user</li>
 *   <li>Score problems based on weighted similarity of users who solved them</li>
 *   <li>Return top N problems sorted by score</li>
 * </ol>
 *
 * <p>Similarity formula:
 * <pre>
 * similarity(u1, u2) = |P(u1) ∩ P(u2)| / sqrt(|P(u1)| * |P(u2)|)
 * </pre>
 */
public class CFRecallStrategy implements RecallStrategy {

    /**
     * Number of most similar users to consider for recommendations.
     */
    private static final int SIMILAR_USER_COUNT = 10;

    /**
     * Priority for this strategy in the recall pipeline.
     */
    private static final int PRIORITY = 30;

    private final UserSimilarityCalculator similarityCalculator;
    private final Map<String, Set<Long>> userProblemMatrix;
    private final List<RecommendItem> availableProblems;

    /**
     * Creates a new CFRecallStrategy with the given user-problem matrix and available problems.
     *
     * @param userProblemMatrix   a map from user ID to the set of problem IDs they have solved
     * @param availableProblems   the list of problems available for recommendation
     */
    public CFRecallStrategy(Map<String, Set<Long>> userProblemMatrix,
                            List<RecommendItem> availableProblems) {
        this.similarityCalculator = new UserSimilarityCalculator();
        this.userProblemMatrix = userProblemMatrix != null ? userProblemMatrix : Map.of();
        this.availableProblems = availableProblems != null ? availableProblems : List.of();
    }

    @Override
    public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
        if (availableProblems.isEmpty() || profile == null) {
            return List.of();
        }

        String currentUserId = profile.getUserId();
        Set<Long> currentUserProblems = getCurrentUserProblems(currentUserId, profile);

        // Cold start: user has no history to compare
        if (currentUserProblems.isEmpty()) {
            return List.of();
        }

        int requestedSize = context != null ? context.getSize() : 10;

        // Find similar users
        List<SimilarUser> similarUsers = findSimilarUsers(currentUserId, currentUserProblems);

        if (similarUsers.isEmpty()) {
            return List.of();
        }

        // Get problems from similar users with weighted scores
        Map<Long, Double> problemScores = calculateProblemScores(similarUsers, currentUserProblems);

        // Build result list sorted by score
        return buildRecommendations(problemScores, requestedSize);
    }

    /**
     * Gets the current user's solved problems from the matrix or profile.
     */
    private Set<Long> getCurrentUserProblems(String userId, UserProfile profile) {
        // Prefer the profile's solved problems if available
        if (profile.getSolvedProblems() != null && !profile.getSolvedProblems().isEmpty()) {
            return profile.getSolvedProblems();
        }

        // Fall back to the matrix
        return userProblemMatrix.getOrDefault(userId, Set.of());
    }

    /**
     * Finds the top K similar users to the current user.
     */
    private List<SimilarUser> findSimilarUsers(String currentUserId, Set<Long> currentUserProblems) {
        return userProblemMatrix.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(currentUserId))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> {
                    double similarity = similarityCalculator.calculateSimilarity(
                            currentUserProblems, entry.getValue());
                    return new SimilarUser(entry.getKey(), similarity, entry.getValue());
                })
                .filter(su -> su.similarity > 0) // Only include users with positive similarity
                .sorted(Comparator.comparingDouble(SimilarUser::similarity).reversed())
                .limit(SIMILAR_USER_COUNT)
                .collect(Collectors.toList());
    }

    /**
     * Calculates weighted scores for problems based on similar users.
     *
     * <p>Each problem's score is the sum of similarities of users who solved it.
     */
    private Map<Long, Double> calculateProblemScores(List<SimilarUser> similarUsers,
                                                      Set<Long> currentUserProblems) {
        Map<Long, Double> problemScores = new HashMap<>();

        for (SimilarUser similarUser : similarUsers) {
            for (Long problemId : similarUser.solvedProblems) {
                // Skip if current user already solved this problem
                if (currentUserProblems.contains(problemId)) {
                    continue;
                }

                // Add weighted score
                problemScores.merge(problemId, similarUser.similarity, Double::sum);
            }
        }

        return problemScores;
    }

    /**
     * Builds the final recommendation list sorted by score.
     */
    private List<RecommendItem> buildRecommendations(Map<Long, Double> problemScores, int requestedSize) {
        // Create a map of problem ID to problem for quick lookup
        Map<Long, RecommendItem> problemMap = availableProblems.stream()
                .collect(Collectors.toMap(RecommendItem::getProblemId, p -> p));

        return problemScores.entrySet().stream()
                .filter(entry -> problemMap.containsKey(entry.getKey()))
                .map(entry -> {
                    RecommendItem original = problemMap.get(entry.getKey());
                    return RecommendItem.builder()
                            .problemId(original.getProblemId())
                            .slug(original.getSlug())
                            .title(original.getTitle())
                            .difficulty(original.getDifficulty())
                            .tags(original.getTags())
                            .score(entry.getValue())
                            .qualityScore(original.getQualityScore())
                            .difficultyMatchScore(original.getDifficultyMatchScore())
                            .tagMatchScore(original.getTagMatchScore())
                            .freshnessScore(original.getFreshnessScore())
                            .reason("Recommended by similar users")
                            .build();
                })
                .sorted(Comparator.comparingDouble(RecommendItem::getScore).reversed())
                .limit(requestedSize)
                .collect(Collectors.toList());
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /**
     * Internal record to hold similar user information.
     */
    private record SimilarUser(String userId, double similarity, Set<Long> solvedProblems) {}
}

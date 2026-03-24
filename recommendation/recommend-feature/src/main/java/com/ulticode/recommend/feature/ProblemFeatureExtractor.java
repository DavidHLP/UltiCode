package com.ulticode.recommend.feature;

import com.ulticode.recommend.feature.model.ProblemFeatures;
import com.ulticode.recommend.feature.model.ProblemInfo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts features from problem metadata for recommendation purposes.
 *
 * <p>This class analyzes problem information to compute various features:
 * <ul>
 *   <li>Difficulty score - normalized from Easy/Medium/Hard</li>
 *   <li>Tag weights - based on tag rarity</li>
 *   <li>Quality score - based on acceptance rate and submission volume</li>
 *   <li>Popularity score - based on likes/dislikes ratio</li>
 *   <li>Categories - high-level groupings of tags</li>
 * </ul>
 */
public class ProblemFeatureExtractor {

    // Difficulty score constants
    private static final double EASY_SCORE = 0.2;
    private static final double MEDIUM_SCORE = 0.5;
    private static final double HARD_SCORE = 0.8;
    private static final double DEFAULT_DIFFICULTY_SCORE = 0.5;

    // Quality score constants
    private static final double VOLUME_WEIGHT = 0.3;
    private static final double ACCEPTANCE_WEIGHT = 0.7;
    private static final int HIGH_VOLUME_THRESHOLD = 10000;

    // Tag category mappings
    private static final Set<String> ALGORITHM_TAGS = Set.of(
            "dynamic-programming", "greedy", "binary-search", "two-pointers",
            "sliding-window", "backtracking", "divide-and-conquer", "recursion",
            "sorting", "search", "bfs", "dfs", "topological-sort", "bit-manipulation",
            "memoization", "minimax", "reservoir-sampling", "geometry"
    );

    private static final Set<String> DATA_STRUCTURE_TAGS = Set.of(
            "array", "linked-list", "stack", "queue", "tree", "graph",
            "hash-table", "heap", "trie", "segment-tree", "binary-indexed-tree",
            "monotonic-stack", "monotonic-queue", "union-find", "design"
    );

    private static final Set<String> MATH_TAGS = Set.of(
            "math", "number-theory", "combinatorics", "probability",
            "random", "prime", "gcd", "lcm", "modular-arithmetic",
            "counting", "brainteaser"
    );

    private static final Set<String> STRING_TAGS = Set.of(
            "string", "string-matching", "palindrome", "rolling-hash",
            "suffix-array", "trie"
    );

    /**
     * Extracts all features from problem metadata.
     *
     * @param problemId  the unique identifier of the problem
     * @param metadata   problem metadata including difficulty, tags, etc.
     * @param stats      submission statistics (totalSubmissions, acceptedSubmissions)
     * @param engagement engagement data (likes, dislikes)
     * @return extracted problem features
     * @throws IllegalArgumentException if problemId is null
     */
    public ProblemFeatures extractFeatures(Long problemId, ProblemInfo metadata,
                                           Map<String, Integer> stats,
                                           Map<String, Integer> engagement) {
        validateInput(problemId);

        // Extract basic metadata
        String slug = metadata != null ? metadata.getSlug() : null;
        String title = metadata != null ? metadata.getTitle() : null;
        String difficulty = metadata != null ? metadata.getDifficulty() : null;
        Set<String> tags = metadata != null && metadata.getTags() != null
                ? new HashSet<>(metadata.getTags())
                : new HashSet<>();

        // Calculate difficulty features
        double difficultyScore = normalizeDifficulty(difficulty);

        // Calculate tag features
        Set<String> categories = categorizeTags(tags);
        Map<String, Double> tagWeights = calculateTagWeights(tags);

        // Calculate quality features
        int totalSubmissions = getStatValue(stats, "totalSubmissions");
        int acceptedSubmissions = getStatValue(stats, "acceptedSubmissions");
        double acceptanceRate = calculateAcceptanceRate(metadata, stats);
        double qualityScore = calculateQualityScore(stats);

        // Calculate popularity features
        int likes = getEngagementValue(engagement, "likes");
        int dislikes = getEngagementValue(engagement, "dislikes");
        double popularityScore = calculatePopularityScore(engagement);

        return ProblemFeatures.builder()
                .problemId(problemId)
                .slug(slug)
                .title(title)
                .difficulty(difficulty)
                .difficultyScore(difficultyScore)
                .tags(tags)
                .categories(categories)
                .tagWeights(tagWeights)
                .acceptanceRate(acceptanceRate)
                .totalSubmissions(totalSubmissions)
                .acceptedSubmissions(acceptedSubmissions)
                .qualityScore(qualityScore)
                .likes(likes)
                .dislikes(dislikes)
                .popularityScore(popularityScore)
                .avgSimilarity(0.0)
                .similarProblems(new HashSet<>())
                .build();
    }

    /**
     * Normalizes difficulty to a score between 0 and 1.
     *
     * @param difficulty the difficulty string ("Easy", "Medium", "Hard")
     * @return normalized difficulty score
     */
    public double normalizeDifficulty(String difficulty) {
        if (difficulty == null) {
            return DEFAULT_DIFFICULTY_SCORE;
        }

        return switch (difficulty.toLowerCase()) {
            case "easy" -> EASY_SCORE;
            case "medium" -> MEDIUM_SCORE;
            case "hard" -> HARD_SCORE;
            default -> DEFAULT_DIFFICULTY_SCORE;
        };
    }

    /**
     * Calculates tag weights based on equal distribution.
     * Each tag gets weight 1/N where N is the total number of tags.
     *
     * @param tags the set of tags
     * @return map of tag to weight
     */
    public Map<String, Double> calculateTagWeights(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new HashMap<>();
        }

        double weight = 1.0 / tags.size();
        return tags.stream()
                .collect(Collectors.toMap(
                        tag -> tag,
                        tag -> weight,
                        (a, b) -> a,
                        HashMap::new
                ));
    }

    /**
     * Calculates quality score based on acceptance rate and submission volume.
     *
     * @param stats map containing totalSubmissions and acceptedSubmissions
     * @return quality score normalized to 0-1
     */
    public double calculateQualityScore(Map<String, Integer> stats) {
        if (stats == null || stats.isEmpty()) {
            return 0.0;
        }

        int totalSubmissions = getStatValue(stats, "totalSubmissions");
        int acceptedSubmissions = getStatValue(stats, "acceptedSubmissions");

        if (totalSubmissions == 0) {
            return 0.0;
        }

        // Calculate acceptance rate component
        double acceptanceRate = (double) acceptedSubmissions / totalSubmissions;

        // Calculate volume component (logarithmic scale)
        double volumeScore = Math.min(1.0, Math.log10(totalSubmissions + 1) / Math.log10(HIGH_VOLUME_THRESHOLD));

        // Combine with weights
        return ACCEPTANCE_WEIGHT * acceptanceRate + VOLUME_WEIGHT * volumeScore;
    }

    /**
     * Calculates popularity score based on likes and dislikes.
     *
     * @param engagement map containing likes and dislikes
     * @return popularity score (likes / (likes + dislikes)), default 0.5 if no engagement
     */
    public double calculatePopularityScore(Map<String, Integer> engagement) {
        if (engagement == null || engagement.isEmpty()) {
            return 0.5;
        }

        int likes = getEngagementValue(engagement, "likes");
        int dislikes = getEngagementValue(engagement, "dislikes");

        int total = likes + dislikes;
        if (total == 0) {
            return 0.5;
        }

        return (double) likes / total;
    }

    /**
     * Categorizes tags into high-level categories.
     *
     * @param tags the set of tags to categorize
     * @return set of category names
     */
    public Set<String> categorizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> categories = new HashSet<>();

        for (String tag : tags) {
            String normalizedTag = tag.toLowerCase();

            if (ALGORITHM_TAGS.contains(normalizedTag)) {
                categories.add("algorithm");
            }
            if (DATA_STRUCTURE_TAGS.contains(normalizedTag)) {
                categories.add("data-structure");
            }
            if (MATH_TAGS.contains(normalizedTag)) {
                categories.add("math");
            }
            if (STRING_TAGS.contains(normalizedTag)) {
                categories.add("string");
            }
        }

        return categories;
    }

    // ==================== Private Helper Methods ====================

    private void validateInput(Long problemId) {
        if (problemId == null) {
            throw new IllegalArgumentException("problemId cannot be null");
        }
    }

    private int getStatValue(Map<String, Integer> stats, String key) {
        if (stats == null) {
            return 0;
        }
        return stats.getOrDefault(key, 0);
    }

    private int getEngagementValue(Map<String, Integer> engagement, String key) {
        if (engagement == null) {
            return 0;
        }
        return engagement.getOrDefault(key, 0);
    }

    private double calculateAcceptanceRate(ProblemInfo metadata, Map<String, Integer> stats) {
        // Prefer acceptance rate from metadata if available
        if (metadata != null && metadata.getAcceptanceRate() != null) {
            return metadata.getAcceptanceRate();
        }

        // Calculate from stats if available
        if (stats != null) {
            int total = getStatValue(stats, "totalSubmissions");
            int accepted = getStatValue(stats, "acceptedSubmissions");

            if (total > 0) {
                return (double) accepted / total;
            }
        }

        return 0.0;
    }
}

package com.ulticode.recommend.feature;

import com.ulticode.recommend.feature.model.ProblemFeatures;
import com.ulticode.recommend.feature.model.ProblemInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProblemFeatureExtractor Tests")
class ProblemFeatureExtractorTest {

    private ProblemFeatureExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ProblemFeatureExtractor();
    }

    @Nested
    @DisplayName("extractFeatures Method Tests")
    class ExtractFeaturesTests {

        @Test
        @DisplayName("Should extract all features from problem metadata")
        void shouldExtractAllFeatures() {
            // Arrange
            Long problemId = 1L;
            String slug = "two-sum";
            String title = "Two Sum";

            ProblemInfo metadata = ProblemInfo.builder()
                    .problemId(problemId)
                    .slug(slug)
                    .title(title)
                    .difficulty("Easy")
                    .tags(Set.of("array", "hash-table"))
                    .acceptanceRate(0.45)
                    .submissionCount(1000)
                    .build();

            Map<String, Integer> stats = new HashMap<>();
            stats.put("totalSubmissions", 1000);
            stats.put("acceptedSubmissions", 450);

            Map<String, Integer> engagement = new HashMap<>();
            engagement.put("likes", 800);
            engagement.put("dislikes", 100);

            // Act
            ProblemFeatures features = extractor.extractFeatures(problemId, metadata, stats, engagement);

            // Assert
            assertNotNull(features);
            assertEquals(problemId, features.getProblemId());
            assertEquals(slug, features.getSlug());
            assertEquals(title, features.getTitle());
            assertEquals("Easy", features.getDifficulty());
            assertTrue(features.getDifficultyScore() >= 0 && features.getDifficultyScore() <= 1);
            assertNotNull(features.getTags());
            assertNotNull(features.getCategories());
            assertNotNull(features.getTagWeights());
            assertTrue(features.getAcceptanceRate() >= 0 && features.getAcceptanceRate() <= 1);
            assertEquals(1000, features.getTotalSubmissions());
            assertEquals(450, features.getAcceptedSubmissions());
            assertTrue(features.getQualityScore() >= 0 && features.getQualityScore() <= 1);
            assertEquals(800, features.getLikes());
            assertEquals(100, features.getDislikes());
            assertTrue(features.getPopularityScore() >= 0 && features.getPopularityScore() <= 1);
        }

        @Test
        @DisplayName("Should handle null metadata gracefully")
        void shouldHandleNullMetadata() {
            // Arrange
            Long problemId = 1L;
            Map<String, Integer> stats = new HashMap<>();
            Map<String, Integer> engagement = new HashMap<>();

            // Act
            ProblemFeatures features = extractor.extractFeatures(problemId, null, stats, engagement);

            // Assert
            assertNotNull(features);
            assertEquals(problemId, features.getProblemId());
            assertNull(features.getSlug());
            assertNull(features.getTitle());
            assertNull(features.getDifficulty());
            assertEquals(0.5, features.getDifficultyScore(), 0.01); // Default for unknown difficulty
            assertTrue(features.getTags() == null || features.getTags().isEmpty());
        }

        @Test
        @DisplayName("Should handle null stats gracefully")
        void shouldHandleNullStats() {
            // Arrange
            Long problemId = 1L;
            ProblemInfo metadata = createProblemInfo(problemId, "Easy", Set.of("array"));

            // Act
            ProblemFeatures features = extractor.extractFeatures(problemId, metadata, null, new HashMap<>());

            // Assert
            assertNotNull(features);
            assertEquals(0, features.getTotalSubmissions());
            assertEquals(0, features.getAcceptedSubmissions());
            assertEquals(0.0, features.getAcceptanceRate());
        }

        @Test
        @DisplayName("Should handle null engagement gracefully")
        void shouldHandleNullEngagement() {
            // Arrange
            Long problemId = 1L;
            ProblemInfo metadata = createProblemInfo(problemId, "Easy", Set.of("array"));

            // Act
            ProblemFeatures features = extractor.extractFeatures(problemId, metadata, new HashMap<>(), null);

            // Assert
            assertNotNull(features);
            assertEquals(0, features.getLikes());
            assertEquals(0, features.getDislikes());
            assertEquals(0.5, features.getPopularityScore(), 0.01); // Default when no engagement
        }

        @Test
        @DisplayName("Should throw exception for null problemId")
        void shouldThrowExceptionForNullProblemId() {
            assertThrows(IllegalArgumentException.class, () ->
                    extractor.extractFeatures(null, createProblemInfo(1L, "Easy", Set.of("array")), new HashMap<>(), new HashMap<>()));
        }
    }

    @Nested
    @DisplayName("normalizeDifficulty Tests")
    class NormalizeDifficultyTests {

        @Test
        @DisplayName("Should normalize Easy to 0.2")
        void shouldNormalizeEasyToTwoTenths() {
            assertEquals(0.2, extractor.normalizeDifficulty("Easy"), 0.001);
        }

        @Test
        @DisplayName("Should normalize Medium to 0.5")
        void shouldNormalizeMediumToHalf() {
            assertEquals(0.5, extractor.normalizeDifficulty("Medium"), 0.001);
        }

        @Test
        @DisplayName("Should normalize Hard to 0.8")
        void shouldNormalizeHardToEightTenths() {
            assertEquals(0.8, extractor.normalizeDifficulty("Hard"), 0.001);
        }

        @Test
        @DisplayName("Should return 0.5 for null difficulty")
        void shouldReturnDefaultForNull() {
            assertEquals(0.5, extractor.normalizeDifficulty(null), 0.001);
        }

        @Test
        @DisplayName("Should return 0.5 for unknown difficulty")
        void shouldReturnDefaultForUnknown() {
            assertEquals(0.5, extractor.normalizeDifficulty("Unknown"), 0.001);
        }

        @Test
        @DisplayName("Should handle case-insensitive difficulty")
        void shouldHandleCaseInsensitive() {
            assertEquals(0.2, extractor.normalizeDifficulty("easy"), 0.001);
            assertEquals(0.5, extractor.normalizeDifficulty("MEDIUM"), 0.001);
            assertEquals(0.8, extractor.normalizeDifficulty("HARD"), 0.001);
        }
    }

    @Nested
    @DisplayName("calculateTagWeights Tests")
    class CalculateTagWeightsTests {

        @Test
        @DisplayName("Should assign equal weights when all tags are same frequency")
        void shouldAssignEqualWeightsForSameFrequency() {
            Set<String> tags = Set.of("array", "hash-table", "two-pointers");
            Map<String, Double> weights = extractor.calculateTagWeights(tags);

            assertEquals(3, weights.size());
            // All weights should be equal (1/3)
            assertTrue(Math.abs(weights.get("array") - 1.0/3.0) < 0.01);
            assertTrue(Math.abs(weights.get("hash-table") - 1.0/3.0) < 0.01);
            assertTrue(Math.abs(weights.get("two-pointers") - 1.0/3.0) < 0.01);
        }

        @Test
        @DisplayName("Should return empty map for empty tags")
        void shouldReturnEmptyMapForEmptyTags() {
            Map<String, Double> weights = extractor.calculateTagWeights(Collections.emptySet());
            assertTrue(weights.isEmpty());
        }

        @Test
        @DisplayName("Should return empty map for null tags")
        void shouldReturnEmptyMapForNullTags() {
            Map<String, Double> weights = extractor.calculateTagWeights(null);
            assertTrue(weights.isEmpty());
        }

        @Test
        @DisplayName("Should assign weight of 1 for single tag")
        void shouldAssignWeightOneForSingleTag() {
            Set<String> tags = Set.of("dynamic-programming");
            Map<String, Double> weights = extractor.calculateTagWeights(tags);

            assertEquals(1, weights.size());
            assertEquals(1.0, weights.get("dynamic-programming"), 0.01);
        }
    }

    @Nested
    @DisplayName("calculateQualityScore Tests")
    class CalculateQualityScoreTests {

        @Test
        @DisplayName("Should calculate quality score based on acceptance rate and volume")
        void shouldCalculateQualityScoreBasedOnAcceptanceRateAndVolume() {
            Map<String, Integer> stats = new HashMap<>();
            stats.put("totalSubmissions", 10000);
            stats.put("acceptedSubmissions", 5000);

            double qualityScore = extractor.calculateQualityScore(stats);

            assertTrue(qualityScore >= 0 && qualityScore <= 1);
            // High volume + 50% acceptance should give decent quality
            assertTrue(qualityScore > 0.4);
        }

        @Test
        @DisplayName("Should return 0 for null stats")
        void shouldReturnZeroForNullStats() {
            assertEquals(0.0, extractor.calculateQualityScore(null), 0.01);
        }

        @Test
        @DisplayName("Should return 0 for empty stats")
        void shouldReturnZeroForEmptyStats() {
            assertEquals(0.0, extractor.calculateQualityScore(new HashMap<>()), 0.01);
        }

        @Test
        @DisplayName("Should return 0 for zero total submissions")
        void shouldReturnZeroForZeroTotalSubmissions() {
            Map<String, Integer> stats = new HashMap<>();
            stats.put("totalSubmissions", 0);
            stats.put("acceptedSubmissions", 0);

            assertEquals(0.0, extractor.calculateQualityScore(stats), 0.01);
        }

        @Test
        @DisplayName("Should handle high acceptance rate with low volume")
        void shouldHandleHighAcceptanceRateWithLowVolume() {
            Map<String, Integer> stats = new HashMap<>();
            stats.put("totalSubmissions", 10);
            stats.put("acceptedSubmissions", 9); // 90% acceptance

            double qualityScore = extractor.calculateQualityScore(stats);

            assertTrue(qualityScore >= 0 && qualityScore <= 1);
            // Low volume should reduce quality score even with high acceptance
        }
    }

    @Nested
    @DisplayName("calculatePopularityScore Tests")
    class CalculatePopularityScoreTests {

        @Test
        @DisplayName("Should calculate popularity based on likes ratio")
        void shouldCalculatePopularityBasedOnLikesRatio() {
            Map<String, Integer> engagement = new HashMap<>();
            engagement.put("likes", 800);
            engagement.put("dislikes", 200);

            double popularity = extractor.calculatePopularityScore(engagement);

            // 800 / (800 + 200) = 0.8
            assertEquals(0.8, popularity, 0.01);
        }

        @Test
        @DisplayName("Should return 1.0 for all likes")
        void shouldReturnOneForAllLikes() {
            Map<String, Integer> engagement = new HashMap<>();
            engagement.put("likes", 1000);
            engagement.put("dislikes", 0);

            assertEquals(1.0, extractor.calculatePopularityScore(engagement), 0.01);
        }

        @Test
        @DisplayName("Should return 0.0 for all dislikes")
        void shouldReturnZeroForAllDislikes() {
            Map<String, Integer> engagement = new HashMap<>();
            engagement.put("likes", 0);
            engagement.put("dislikes", 1000);

            assertEquals(0.0, extractor.calculatePopularityScore(engagement), 0.01);
        }

        @Test
        @DisplayName("Should return 0.5 default for no engagement")
        void shouldReturnDefaultForNoEngagement() {
            assertEquals(0.5, extractor.calculatePopularityScore(null), 0.01);
            assertEquals(0.5, extractor.calculatePopularityScore(new HashMap<>()), 0.01);
        }

        @Test
        @DisplayName("Should handle missing likes or dislikes")
        void shouldHandleMissingLikesOrDislikes() {
            Map<String, Integer> noLikes = new HashMap<>();
            noLikes.put("dislikes", 100);
            assertEquals(0.0, extractor.calculatePopularityScore(noLikes), 0.01);

            Map<String, Integer> noDislikes = new HashMap<>();
            noDislikes.put("likes", 100);
            assertEquals(1.0, extractor.calculatePopularityScore(noDislikes), 0.01);
        }
    }

    @Nested
    @DisplayName("categorizeTags Tests")
    class CategorizeTagsTests {

        @Test
        @DisplayName("Should categorize algorithm tags")
        void shouldCategorizeAlgorithmTags() {
            Set<String> tags = Set.of("dynamic-programming", "greedy", "binary-search", "two-pointers");
            Set<String> categories = extractor.categorizeTags(tags);

            assertTrue(categories.contains("algorithm"));
        }

        @Test
        @DisplayName("Should categorize data structure tags")
        void shouldCategorizeDataStructureTags() {
            Set<String> tags = Set.of("array", "linked-list", "tree", "graph", "hash-table");
            Set<String> categories = extractor.categorizeTags(tags);

            assertTrue(categories.contains("data-structure"));
        }

        @Test
        @DisplayName("Should categorize math tags")
        void shouldCategorizeMathTags() {
            Set<String> tags = Set.of("math", "number-theory", "combinatorics", "probability");
            Set<String> categories = extractor.categorizeTags(tags);

            assertTrue(categories.contains("math"));
        }

        @Test
        @DisplayName("Should categorize string tags")
        void shouldCategorizeStringTags() {
            Set<String> tags = Set.of("string", "string-matching", "palindrome");
            Set<String> categories = extractor.categorizeTags(tags);

            assertTrue(categories.contains("string"));
        }

        @Test
        @DisplayName("Should handle mixed tags")
        void shouldHandleMixedTags() {
            Set<String> tags = Set.of("array", "dynamic-programming", "string", "math");
            Set<String> categories = extractor.categorizeTags(tags);

            assertTrue(categories.contains("algorithm"));
            assertTrue(categories.contains("data-structure"));
            assertTrue(categories.contains("string"));
            assertTrue(categories.contains("math"));
        }

        @Test
        @DisplayName("Should return empty set for null tags")
        void shouldReturnEmptySetForNullTags() {
            Set<String> categories = extractor.categorizeTags(null);
            assertTrue(categories.isEmpty());
        }

        @Test
        @DisplayName("Should return empty set for empty tags")
        void shouldReturnEmptySetForEmptyTags() {
            Set<String> categories = extractor.categorizeTags(Collections.emptySet());
            assertTrue(categories.isEmpty());
        }

        @Test
        @DisplayName("Should handle unknown tags gracefully")
        void shouldHandleUnknownTags() {
            Set<String> tags = Set.of("unknown-tag-xyz");
            Set<String> categories = extractor.categorizeTags(tags);

            // Unknown tags should not add any category
            assertTrue(categories.isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Integration Tests")
    class EdgeCasesAndIntegrationTests {

        @Test
        @DisplayName("Should handle problem with no tags")
        void shouldHandleProblemWithNoTags() {
            Long problemId = 1L;
            ProblemInfo metadata = ProblemInfo.builder()
                    .problemId(problemId)
                    .difficulty("Medium")
                    .tags(Collections.emptySet())
                    .build();

            Map<String, Integer> stats = new HashMap<>();
            stats.put("totalSubmissions", 100);
            stats.put("acceptedSubmissions", 50);

            ProblemFeatures features = extractor.extractFeatures(problemId, metadata, stats, new HashMap<>());

            assertNotNull(features);
            assertTrue(features.getTags() == null || features.getTags().isEmpty());
            assertTrue(features.getCategories().isEmpty());
            assertTrue(features.getTagWeights().isEmpty());
        }

        @Test
        @DisplayName("Should calculate all features consistently")
        void shouldCalculateAllFeaturesConsistently() {
            Long problemId = 42L;
            ProblemInfo metadata = ProblemInfo.builder()
                    .problemId(problemId)
                    .slug("median-of-two-sorted-arrays")
                    .title("Median of Two Sorted Arrays")
                    .difficulty("Hard")
                    .tags(Set.of("array", "binary-search", "divide-and-conquer"))
                    .acceptanceRate(0.35)
                    .submissionCount(500000)
                    .build();

            Map<String, Integer> stats = new HashMap<>();
            stats.put("totalSubmissions", 500000);
            stats.put("acceptedSubmissions", 175000);

            Map<String, Integer> engagement = new HashMap<>();
            engagement.put("likes", 5000);
            engagement.put("dislikes", 500);

            ProblemFeatures features = extractor.extractFeatures(problemId, metadata, stats, engagement);

            // Verify all fields are populated
            assertEquals(42L, features.getProblemId());
            assertEquals("median-of-two-sorted-arrays", features.getSlug());
            assertEquals("Median of Two Sorted Arrays", features.getTitle());
            assertEquals("Hard", features.getDifficulty());
            assertEquals(0.8, features.getDifficultyScore(), 0.01);
            assertEquals(3, features.getTags().size());
            assertTrue(features.getCategories().contains("algorithm"));
            assertTrue(features.getCategories().contains("data-structure"));
            assertEquals(0.35, features.getAcceptanceRate(), 0.01);
            assertEquals(500000, features.getTotalSubmissions());
            assertEquals(175000, features.getAcceptedSubmissions());
            assertTrue(features.getQualityScore() > 0);
            assertEquals(5000, features.getLikes());
            assertEquals(500, features.getDislikes());
            assertEquals(5000.0 / 5500, features.getPopularityScore(), 0.01);
        }

        @Test
        @DisplayName("Should handle Medium difficulty correctly")
        void shouldHandleMediumDifficultyCorrectly() {
            Long problemId = 1L;
            ProblemInfo metadata = createProblemInfo(problemId, "Medium", Set.of("array"));
            Map<String, Integer> stats = new HashMap<>();
            Map<String, Integer> engagement = new HashMap<>();

            ProblemFeatures features = extractor.extractFeatures(problemId, metadata, stats, engagement);

            assertEquals("Medium", features.getDifficulty());
            assertEquals(0.5, features.getDifficultyScore(), 0.01);
        }

        @Test
        @DisplayName("Should handle Easy difficulty correctly")
        void shouldHandleEasyDifficultyCorrectly() {
            Long problemId = 1L;
            ProblemInfo metadata = createProblemInfo(problemId, "Easy", Set.of("array"));
            Map<String, Integer> stats = new HashMap<>();
            Map<String, Integer> engagement = new HashMap<>();

            ProblemFeatures features = extractor.extractFeatures(problemId, metadata, stats, engagement);

            assertEquals("Easy", features.getDifficulty());
            assertEquals(0.2, features.getDifficultyScore(), 0.01);
        }
    }

    // Helper methods

    private ProblemInfo createProblemInfo(Long problemId, String difficulty, Set<String> tags) {
        return ProblemInfo.builder()
                .problemId(problemId)
                .difficulty(difficulty)
                .tags(tags != null ? tags : Collections.emptySet())
                .build();
    }
}

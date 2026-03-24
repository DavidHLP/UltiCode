package com.ulticode.recommend.core.rank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RuleRankStrategyTest {

    private RuleRankStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RuleRankStrategy();
    }

    @Nested
    @DisplayName("Difficulty Match Calculation")
    class DifficultyMatchTests {

        @Test
        @DisplayName("should return 1.0 when difficulty matches user rating")
        void difficultyMatch_exactMatch_returnsOne() {
            // User rating 1500 -> expected Medium difficulty
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .build();

            RecommendItem item = createItem(1L, "Medium", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(1.0, result.get(0).getDifficultyMatchScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.5 for adjacent difficulty")
        void difficultyMatch_adjacent_returnsHalf() {
            // User rating 1500 -> expected Medium, but item is Easy
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .build();

            RecommendItem item = createItem(1L, "Easy", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.5, result.get(0).getDifficultyMatchScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.0 for non-adjacent difficulty")
        void difficultyMatch_nonAdjacent_returnsZero() {
            // User rating 1000 -> expected Easy, but item is Hard
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1000)
                .build();

            RecommendItem item = createItem(1L, "Hard", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.0, result.get(0).getDifficultyMatchScore(), 0.001);
        }

        @Test
        @DisplayName("rating 1199 should expect Easy")
        void difficultyMatch_rating1199_expectsEasy() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1199)
                .build();

            RecommendItem easyItem = createItem(1L, "Easy", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(easyItem), createDefaultContext(), profile);

            assertEquals(1.0, result.get(0).getDifficultyMatchScore(), 0.001);
        }

        @Test
        @DisplayName("rating 1800 should expect Hard")
        void difficultyMatch_rating1800_expectsHard() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1800)
                .build();

            RecommendItem hardItem = createItem(1L, "Hard", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(hardItem), createDefaultContext(), profile);

            assertEquals(1.0, result.get(0).getDifficultyMatchScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Tag Match Calculation")
    class TagMatchTests {

        @Test
        @DisplayName("should return 0.5 when no tags or mastery data")
        void tagMatch_noTags_returnsNeutral() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .build();

            RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")
                .qualityScore(0.8)
                .build();

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.5, result.get(0).getTagMatchScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.0 when no tags match user mastery")
        void tagMatch_noMatches_returnsZero() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(Map.of("dp", 0.8, "graph", 0.6))
                .build();

            RecommendItem item = createItem(1L, "Medium", Set.of("array", "math"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.0, result.get(0).getTagMatchScore(), 0.001);
        }

        @Test
        @DisplayName("should calculate weighted match when tags partially match")
        void tagMatch_partialMatch_calculatesWeighted() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(Map.of("array", 0.8, "dp", 0.6))
                .build();

            // Item has 3 tags, 1 matches with mastery 0.8
            RecommendItem item = createItem(1L, "Medium", Set.of("array", "math", "sorting"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            // avgMastery = 0.8, matchRatio = 1/3
            // score = 0.8 * (1/3) = 0.267
            assertEquals(0.267, result.get(0).getTagMatchScore(), 0.01);
        }

        @Test
        @DisplayName("should return high score when all tags match with high mastery")
        void tagMatch_allMatchHighMastery_returnsHighScore() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(Map.of("array", 0.9, "sorting", 0.9))
                .build();

            RecommendItem item = createItem(1L, "Medium", Set.of("array", "sorting"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            // avgMastery = 0.9, matchRatio = 1.0
            // score = 0.9 * 1.0 = 0.9
            assertEquals(0.9, result.get(0).getTagMatchScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Freshness Calculation")
    class FreshnessTests {

        @Test
        @DisplayName("should return 1.0 for problems created within 7 days")
        void freshness_veryFresh_returnsOne() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = createItemWithCreatedAt(1L, "Medium", LocalDateTime.now().minusDays(3));

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(1.0, result.get(0).getFreshnessScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.8 for problems created 8-30 days ago")
        void freshness_fresh_returnsPointEight() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = createItemWithCreatedAt(1L, "Medium", LocalDateTime.now().minusDays(15));

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.8, result.get(0).getFreshnessScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.6 for problems created 31-90 days ago")
        void freshness_moderate_returnsPointSix() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = createItemWithCreatedAt(1L, "Medium", LocalDateTime.now().minusDays(60));

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.6, result.get(0).getFreshnessScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.4 for problems created 91-365 days ago")
        void freshness_somewhatOld_returnsPointFour() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = createItemWithCreatedAt(1L, "Medium", LocalDateTime.now().minusDays(180));

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.4, result.get(0).getFreshnessScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.2 for problems older than 365 days")
        void freshness_stale_returnsPointTwo() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = createItemWithCreatedAt(1L, "Medium", LocalDateTime.now().minusDays(400));

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.2, result.get(0).getFreshnessScore(), 0.001);
        }

        @Test
        @DisplayName("should return 0.5 when createdAt is null")
        void freshness_nullCreatedAt_returnsNeutral() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")
                .qualityScore(0.8)
                .build();

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.5, result.get(0).getFreshnessScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Quality Score")
    class QualityScoreTests {

        @Test
        @DisplayName("should use existing quality score from item")
        void qualityScore_usesExistingScore() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = createItem(1L, "Medium", Set.of("array"), 0.75);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.75, result.get(0).getQualityScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Final Score Calculation")
    class FinalScoreTests {

        @Test
        @DisplayName("should calculate weighted sum of all factors")
        void finalScore_weightedSum() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)  // expects Medium
                .tagMastery(Map.of("array", 1.0))
                .build();

            RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")  // difficultyMatch = 1.0
                .tags(Set.of("array"))  // tagMatch = 1.0 * 1.0 = 1.0
                .qualityScore(1.0)      // quality = 1.0
                .createdAt(LocalDateTime.now().minusDays(3))  // freshness = 1.0
                .build();

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            // score = 0.35*1.0 + 0.30*1.0 + 0.15*1.0 + 0.20*1.0 = 1.0
            assertEquals(1.0, result.get(0).getScore(), 0.001);
        }

        @Test
        @DisplayName("should calculate correct score with mixed factors")
        void finalScore_mixedFactors() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1000)  // expects Easy
                .tagMastery(Map.of("array", 0.5))
                .build();

            RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")  // difficultyMatch = 0.5 (adjacent)
                .tags(Set.of("array"))  // tagMatch = 0.5 * 1.0 = 0.5
                .qualityScore(0.8)      // quality = 0.8
                .createdAt(LocalDateTime.now().minusDays(15))  // freshness = 0.8
                .build();

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            // score = 0.35*0.5 + 0.30*0.5 + 0.15*0.8 + 0.20*0.8
            //       = 0.175 + 0.15 + 0.12 + 0.16 = 0.605
            assertEquals(0.605, result.get(0).getScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Sorting")
    class SortingTests {

        @Test
        @DisplayName("should sort items by score descending")
        void sorting_descendingByScore() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(Map.of("array", 1.0, "dp", 0.3))
                .build();

            RecommendItem highScore = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")
                .tags(Set.of("array"))  // high tag match
                .qualityScore(0.9)
                .createdAt(LocalDateTime.now())
                .build();

            RecommendItem lowScore = RecommendItem.builder()
                .problemId(2L)
                .difficulty("Medium")
                .tags(Set.of("dp"))  // low tag match
                .qualityScore(0.5)
                .createdAt(LocalDateTime.now().minusDays(400))
                .build();

            List<RecommendItem> result = strategy.rank(List.of(lowScore, highScore), createDefaultContext(), profile);

            assertEquals(1L, result.get(0).getProblemId());
            assertEquals(2L, result.get(1).getProblemId());
            assertTrue(result.get(0).getScore() > result.get(1).getScore());
        }

        @Test
        @DisplayName("should maintain stable sort for equal scores")
        void sorting_stableForEqualScores() {
            UserProfile profile = createDefaultProfile();

            RecommendItem item1 = createItem(1L, "Medium", Set.of("array"), 0.8);
            RecommendItem item2 = createItem(2L, "Medium", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item1, item2), createDefaultContext(), profile);

            assertEquals(1L, result.get(0).getProblemId());
            assertEquals(2L, result.get(1).getProblemId());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should return empty list for null input")
        void edgeCase_nullInput_returnsEmpty() {
            UserProfile profile = createDefaultProfile();

            List<RecommendItem> result = strategy.rank(null, createDefaultContext(), profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty list for empty input")
        void edgeCase_emptyInput_returnsEmpty() {
            UserProfile profile = createDefaultProfile();

            List<RecommendItem> result = strategy.rank(List.of(), createDefaultContext(), profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle null profile gracefully")
        void edgeCase_nullProfile() {
            RecommendItem item = createItem(1L, "Medium", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), null);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("should handle null context gracefully")
        void edgeCase_nullContext() {
            UserProfile profile = createDefaultProfile();
            RecommendItem item = createItem(1L, "Medium", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), null, profile);

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("should handle item with null tags")
        void edgeCase_nullTags() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(Map.of("array", 0.8))
                .build();

            RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")
                .tags(null)
                .qualityScore(0.8)
                .build();

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.5, result.get(0).getTagMatchScore(), 0.001);
        }

        @Test
        @DisplayName("should handle item with empty tags")
        void edgeCase_emptyTags() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(Map.of("array", 0.8))
                .build();

            RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .difficulty("Medium")
                .tags(Set.of())
                .qualityScore(0.8)
                .build();

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.5, result.get(0).getTagMatchScore(), 0.001);
        }

        @Test
        @DisplayName("should handle profile with null tagMastery")
        void edgeCase_nullTagMastery() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(null)
                .build();

            RecommendItem item = createItem(1L, "Medium", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.5, result.get(0).getTagMatchScore(), 0.001);
        }

        @Test
        @DisplayName("should handle profile with empty tagMastery")
        void edgeCase_emptyTagMastery() {
            UserProfile profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .tagMastery(Map.of())
                .build();

            RecommendItem item = createItem(1L, "Medium", Set.of("array"), 0.8);

            List<RecommendItem> result = strategy.rank(List.of(item), createDefaultContext(), profile);

            assertEquals(0.5, result.get(0).getTagMatchScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Strategy Metadata")
    class MetadataTests {

        @Test
        @DisplayName("should return class simple name as strategy name")
        void metadata_name() {
            assertEquals("RuleRankStrategy", strategy.getName());
        }

        @Test
        @DisplayName("should return priority 100")
        void metadata_priority() {
            assertEquals(100, strategy.getPriority());
        }
    }

    // Helper methods

    private RecommendItem createItem(Long problemId, String difficulty, Set<String> tags, double qualityScore) {
        return RecommendItem.builder()
            .problemId(problemId)
            .difficulty(difficulty)
            .tags(tags)
            .qualityScore(qualityScore)
            .createdAt(LocalDateTime.now().minusDays(30))
            .build();
    }

    private RecommendItem createItemWithCreatedAt(Long problemId, String difficulty, LocalDateTime createdAt) {
        return RecommendItem.builder()
            .problemId(problemId)
            .difficulty(difficulty)
            .tags(Set.of("array"))
            .qualityScore(0.8)
            .createdAt(createdAt)
            .build();
    }

    private UserProfile createDefaultProfile() {
        return UserProfile.builder()
            .userId("user1")
            .rating(1500)
            .build();
    }

    private RecommendContext createDefaultContext() {
        return RecommendContext.builder()
            .userId("user1")
            .build();
    }
}

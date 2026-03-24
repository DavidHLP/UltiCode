package com.ulticode.recommend.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecommendItem Tests")
class RecommendItemTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create item using builder pattern")
        void shouldCreateItemUsingBuilder() {
            RecommendItem item = RecommendItem.builder()
                    .problemId(1L)
                    .slug("two-sum")
                    .title("Two Sum")
                    .difficulty("Easy")
                    .score(0.85)
                    .tags(Set.of("array", "hash-table"))
                    .reason("Similar to your solved problems")
                    .build();

            assertNotNull(item);
            assertEquals(1L, item.getProblemId());
            assertEquals("two-sum", item.getSlug());
            assertEquals("Two Sum", item.getTitle());
            assertEquals("Easy", item.getDifficulty());
            assertEquals(0.85, item.getScore(), 0.001);
            assertEquals(Set.of("array", "hash-table"), item.getTags());
            assertEquals("Similar to your solved problems", item.getReason());
        }

        @Test
        @DisplayName("Should create item with all score components")
        void shouldCreateItemWithAllScoreComponents() {
            RecommendItem item = RecommendItem.builder()
                    .problemId(1L)
                    .difficultyMatchScore(0.9)
                    .tagMatchScore(0.8)
                    .freshnessScore(0.7)
                    .qualityScore(0.85)
                    .build();

            assertEquals(0.9, item.getDifficultyMatchScore(), 0.001);
            assertEquals(0.8, item.getTagMatchScore(), 0.001);
            assertEquals(0.7, item.getFreshnessScore(), 0.001);
            assertEquals(0.85, item.getQualityScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Calculate Final Score Tests")
    class CalculateFinalScoreTests {

        @Test
        @DisplayName("Should calculate final score with correct weights")
        void shouldCalculateFinalScoreWithCorrectWeights() {
            RecommendItem item = RecommendItem.builder()
                    .difficultyMatchScore(1.0)
                    .tagMatchScore(1.0)
                    .freshnessScore(1.0)
                    .qualityScore(1.0)
                    .build();

            // All weights should sum to 1.0
            // WEIGHT_DIFFICULTY = 0.35, WEIGHT_TAG = 0.30, WEIGHT_FRESHNESS = 0.15, WEIGHT_QUALITY = 0.20
            double expectedScore = 0.35 * 1.0 + 0.30 * 1.0 + 0.15 * 1.0 + 0.20 * 1.0;
            assertEquals(expectedScore, item.calculateFinalScore(), 0.001);
        }

        @Test
        @DisplayName("Should calculate final score with partial values")
        void shouldCalculateFinalScoreWithPartialValues() {
            RecommendItem item = RecommendItem.builder()
                    .difficultyMatchScore(0.8)
                    .tagMatchScore(0.6)
                    .freshnessScore(0.9)
                    .qualityScore(0.7)
                    .build();

            // 0.35 * 0.8 + 0.30 * 0.6 + 0.15 * 0.9 + 0.20 * 0.7
            double expectedScore = 0.35 * 0.8 + 0.30 * 0.6 + 0.15 * 0.9 + 0.20 * 0.7;
            assertEquals(expectedScore, item.calculateFinalScore(), 0.001);
        }

        @Test
        @DisplayName("Should calculate final score with zero values")
        void shouldCalculateFinalScoreWithZeroValues() {
            RecommendItem item = RecommendItem.builder()
                    .difficultyMatchScore(0.0)
                    .tagMatchScore(0.0)
                    .freshnessScore(0.0)
                    .qualityScore(0.0)
                    .build();

            assertEquals(0.0, item.calculateFinalScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Comparable Tests")
    class ComparableTests {

        @Test
        @DisplayName("Should compare items by score in descending order")
        void shouldCompareItemsByScoreDescending() {
            RecommendItem item1 = RecommendItem.builder().score(0.9).build();
            RecommendItem item2 = RecommendItem.builder().score(0.8).build();
            RecommendItem item3 = RecommendItem.builder().score(0.85).build();

            // item1 (0.9) should come before item2 (0.8) -> item1.compareTo(item2) < 0
            assertTrue(item1.compareTo(item2) < 0);
            assertTrue(item2.compareTo(item1) > 0);
            assertTrue(item1.compareTo(item3) < 0);
            assertEquals(0, item1.compareTo(RecommendItem.builder().score(0.9).build()));
        }

        @Test
        @DisplayName("Should sort items in descending order by score")
        void shouldSortItemsInDescendingOrder() {
            java.util.List<RecommendItem> items = java.util.Arrays.asList(
                    RecommendItem.builder().score(0.7).build(),
                    RecommendItem.builder().score(0.9).build(),
                    RecommendItem.builder().score(0.8).build()
            );

            java.util.Collections.sort(items);

            assertEquals(0.9, items.get(0).getScore(), 0.001);
            assertEquals(0.8, items.get(1).getScore(), 0.001);
            assertEquals(0.7, items.get(2).getScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null tags")
        void shouldHandleNullTags() {
            RecommendItem item = RecommendItem.builder()
                    .problemId(1L)
                    .tags(null)
                    .build();

            assertNull(item.getTags());
        }

        @Test
        @DisplayName("Should handle empty tags")
        void shouldHandleEmptyTags() {
            RecommendItem item = RecommendItem.builder()
                    .problemId(1L)
                    .tags(Set.of())
                    .build();

            assertNotNull(item.getTags());
            assertTrue(item.getTags().isEmpty());
        }

        @Test
        @DisplayName("Should create item with no-args constructor")
        void shouldCreateItemWithNoArgsConstructor() {
            RecommendItem item = new RecommendItem();
            assertNotNull(item);
        }

        @Test
        @DisplayName("Should create item with all-args constructor")
        void shouldCreateItemWithAllArgsConstructor() {
            Set<String> tags = Set.of("array");
            LocalDateTime createdAt = LocalDateTime.now().minusDays(10);
            RecommendItem item = new RecommendItem(
                    1L, "two-sum", "Two Sum", "Easy", 0.85,
                    tags, "Test reason", createdAt,
                    0.9, 0.8, 0.7, 0.85
            );

            assertEquals(1L, item.getProblemId());
            assertEquals("two-sum", item.getSlug());
            assertEquals("Two Sum", item.getTitle());
            assertEquals("Easy", item.getDifficulty());
            assertEquals(0.85, item.getScore(), 0.001);
            assertEquals(tags, item.getTags());
            assertEquals("Test reason", item.getReason());
            assertEquals(createdAt, item.getCreatedAt());
            assertEquals(0.9, item.getDifficultyMatchScore(), 0.001);
            assertEquals(0.8, item.getTagMatchScore(), 0.001);
            assertEquals(0.7, item.getFreshnessScore(), 0.001);
            assertEquals(0.85, item.getQualityScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("Weight Constants Tests")
    class WeightConstantsTests {

        @Test
        @DisplayName("Should have correct weight constants")
        void shouldHaveCorrectWeightConstants() throws Exception {
            // Verify weights using reflection
            var weightDifficulty = RecommendItem.class.getDeclaredField("WEIGHT_DIFFICULTY");
            weightDifficulty.setAccessible(true);
            assertEquals(0.35, weightDifficulty.getDouble(null), 0.001);

            var weightTag = RecommendItem.class.getDeclaredField("WEIGHT_TAG");
            weightTag.setAccessible(true);
            assertEquals(0.30, weightTag.getDouble(null), 0.001);

            var weightFreshness = RecommendItem.class.getDeclaredField("WEIGHT_FRESHNESS");
            weightFreshness.setAccessible(true);
            assertEquals(0.15, weightFreshness.getDouble(null), 0.001);

            var weightQuality = RecommendItem.class.getDeclaredField("WEIGHT_QUALITY");
            weightQuality.setAccessible(true);
            assertEquals(0.20, weightQuality.getDouble(null), 0.001);
        }

        @Test
        @DisplayName("Should have weights that sum to 1.0")
        void shouldHaveWeightsSumToOne() throws Exception {
            var weightDifficulty = RecommendItem.class.getDeclaredField("WEIGHT_DIFFICULTY");
            weightDifficulty.setAccessible(true);
            double w1 = weightDifficulty.getDouble(null);

            var weightTag = RecommendItem.class.getDeclaredField("WEIGHT_TAG");
            weightTag.setAccessible(true);
            double w2 = weightTag.getDouble(null);

            var weightFreshness = RecommendItem.class.getDeclaredField("WEIGHT_FRESHNESS");
            weightFreshness.setAccessible(true);
            double w3 = weightFreshness.getDouble(null);

            var weightQuality = RecommendItem.class.getDeclaredField("WEIGHT_QUALITY");
            weightQuality.setAccessible(true);
            double w4 = weightQuality.getDouble(null);

            assertEquals(1.0, w1 + w2 + w3 + w4, 0.001);
        }
    }
}

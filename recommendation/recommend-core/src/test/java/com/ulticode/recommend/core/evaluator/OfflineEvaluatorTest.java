package com.ulticode.recommend.core.evaluator;

import com.ulticode.recommend.core.model.RecommendItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OfflineEvaluator Tests")
class OfflineEvaluatorTest {

    private final OfflineEvaluator evaluator = new OfflineEvaluator();

    @Nested
    @DisplayName("Precision@K Tests")
    class PrecisionAtKTests {

        @Test
        @DisplayName("Should return 1.0 when all recommended items are relevant")
        void shouldReturnOneWhenAllRecommendedAreRelevant() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L));

            double precision = evaluator.calculatePrecisionAtK(recommended, relevant, 5);

            assertEquals(1.0, precision, 0.001);
        }

        @Test
        @DisplayName("Should return 0.0 when no recommended items are relevant")
        void shouldReturnZeroWhenNoRecommendedAreRelevant() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(6L, 7L, 8L));

            double precision = evaluator.calculatePrecisionAtK(recommended, relevant, 5);

            assertEquals(0.0, precision, 0.001);
        }

        @Test
        @DisplayName("Should calculate precision correctly for partial overlap")
        void shouldCalculatePrecisionForPartialOverlap() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(2L, 4L, 6L));

            // At K=5, relevant in top 5 are [2, 4] = 2 items
            // Precision = 2/5 = 0.4
            double precision = evaluator.calculatePrecisionAtK(recommended, relevant, 5);

            assertEquals(0.4, precision, 0.001);
        }

        @Test
        @DisplayName("Should respect K parameter for precision")
        void shouldRespectKParameterForPrecision() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 3L, 5L, 7L));

            // At K=3, relevant in top 3 are [1, 3] = 2 items
            // Precision = 2/3 = 0.666...
            double precision = evaluator.calculatePrecisionAtK(recommended, relevant, 3);

            assertEquals(2.0 / 3.0, precision, 0.001);
        }

        @Test
        @DisplayName("Should handle empty recommended list for precision")
        void shouldHandleEmptyRecommendedForPrecision() {
            List<Long> recommended = Collections.emptyList();
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            double precision = evaluator.calculatePrecisionAtK(recommended, relevant, 5);

            assertEquals(0.0, precision, 0.001);
        }

        @Test
        @DisplayName("Should handle empty relevant set for precision")
        void shouldHandleEmptyRelevantForPrecision() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = Collections.emptySet();

            double precision = evaluator.calculatePrecisionAtK(recommended, relevant, 5);

            assertEquals(0.0, precision, 0.001);
        }

        @Test
        @DisplayName("Should handle K greater than recommended list size")
        void shouldHandleKGreaterThanRecommendedSize() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L));

            // K=10 but only 3 items, relevant are [1, 2] = 2 items
            // Precision = 2/3 = 0.666...
            double precision = evaluator.calculatePrecisionAtK(recommended, relevant, 10);

            assertEquals(2.0 / 3.0, precision, 0.001);
        }
    }

    @Nested
    @DisplayName("Recall@K Tests")
    class RecallAtKTests {

        @Test
        @DisplayName("Should return 1.0 when all relevant items are recommended")
        void shouldReturnOneWhenAllRelevantAreRecommended() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            double recall = evaluator.calculateRecallAtK(recommended, relevant, 5);

            assertEquals(1.0, recall, 0.001);
        }

        @Test
        @DisplayName("Should return 0.0 when no relevant items are recommended")
        void shouldReturnZeroWhenNoRelevantAreRecommended() {
            List<Long> recommended = Arrays.asList(6L, 7L, 8L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            double recall = evaluator.calculateRecallAtK(recommended, relevant, 5);

            assertEquals(0.0, recall, 0.001);
        }

        @Test
        @DisplayName("Should calculate recall correctly for partial overlap")
        void shouldCalculateRecallForPartialOverlap() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(2L, 4L, 6L));

            // Relevant items are {2, 4, 6}, recommended relevant are [2, 4]
            // Recall = 2/3 = 0.666...
            double recall = evaluator.calculateRecallAtK(recommended, relevant, 5);

            assertEquals(2.0 / 3.0, recall, 0.001);
        }

        @Test
        @DisplayName("Should respect K parameter for recall")
        void shouldRespectKParameterForRecall() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(3L, 4L, 5L, 6L));

            // At K=3, top 3 are [1, 2, 3], relevant in top 3 is [3]
            // Total relevant = 4, found = 1
            // Recall = 1/4 = 0.25
            double recall = evaluator.calculateRecallAtK(recommended, relevant, 3);

            assertEquals(0.25, recall, 0.001);
        }

        @Test
        @DisplayName("Should handle empty relevant set for recall")
        void shouldHandleEmptyRelevantForRecall() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = Collections.emptySet();

            double recall = evaluator.calculateRecallAtK(recommended, relevant, 5);

            assertEquals(0.0, recall, 0.001);
        }
    }

    @Nested
    @DisplayName("F1-Score Tests")
    class F1ScoreTests {

        @Test
        @DisplayName("Should calculate F1 score correctly")
        void shouldCalculateF1Correctly() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(2L, 4L, 6L));

            // Precision = 2/5 = 0.4
            // Recall = 2/3 = 0.666...
            // F1 = 2 * (0.4 * 0.666...) / (0.4 + 0.666...) = 0.5
            OfflineMetrics metrics = evaluator.evaluate(recommended, relevant, 5, null, 0);

            assertEquals(0.5, metrics.getF1Score(), 0.001);
        }

        @Test
        @DisplayName("Should return 0 when precision and recall are both 0")
        void shouldReturnZeroWhenPrecisionAndRecallAreZero() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(4L, 5L, 6L));

            OfflineMetrics metrics = evaluator.evaluate(recommended, relevant, 5, null, 0);

            assertEquals(0.0, metrics.getF1Score(), 0.001);
        }

        @Test
        @DisplayName("Should return 1.0 when precision and recall are both 1.0")
        void shouldReturnOneWhenPrecisionAndRecallAreOne() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            OfflineMetrics metrics = evaluator.evaluate(recommended, relevant, 5, null, 0);

            assertEquals(1.0, metrics.getF1Score(), 0.001);
        }
    }

    @Nested
    @DisplayName("NDCG@K Tests")
    class NDCGAtKTests {

        @Test
        @DisplayName("Should return 1.0 for perfect ranking")
        void shouldReturnOneForPerfectRanking() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            double ndcg = evaluator.calculateNDCGAtK(recommended, relevant, 3);

            assertEquals(1.0, ndcg, 0.001);
        }

        @Test
        @DisplayName("Should return 0.0 when no items are relevant")
        void shouldReturnZeroWhenNoItemsRelevant() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(4L, 5L, 6L));

            double ndcg = evaluator.calculateNDCGAtK(recommended, relevant, 3);

            assertEquals(0.0, ndcg, 0.001);
        }

        @Test
        @DisplayName("Should penalize lower positions in ranking")
        void shouldPenalizeLowerPositions() {
            // Relevant items at the end
            List<Long> recommended = Arrays.asList(4L, 5L, 1L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            double ndcg = evaluator.calculateNDCGAtK(recommended, relevant, 3);

            // Should be less than 1.0 because relevant item is at position 3
            assertTrue(ndcg < 1.0);
            assertTrue(ndcg > 0.0);
        }

        @Test
        @DisplayName("Should calculate NDCG correctly for mixed results")
        void shouldCalculateNDCGCorrectlyForMixedResults() {
            // Items 1, 3 are relevant; 2, 4 are not
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 3L, 5L));

            double ndcg = evaluator.calculateNDCGAtK(recommended, relevant, 4);

            // DCG = 1/log2(2) + 0 + 1/log2(4) + 0 = 1 + 0.5 = 1.5
            // IDCG = 1/log2(2) + 1/log2(3) + 1/log2(4) = 1 + 0.631 + 0.5 = 2.131
            // NDCG = 1.5 / 2.131 ≈ 0.704
            assertTrue(ndcg > 0.6 && ndcg < 0.8);
        }

        @Test
        @DisplayName("Should handle empty recommended list")
        void shouldHandleEmptyRecommendedForNDCG() {
            List<Long> recommended = Collections.emptyList();
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            double ndcg = evaluator.calculateNDCGAtK(recommended, relevant, 5);

            assertEquals(0.0, ndcg, 0.001);
        }
    }

    @Nested
    @DisplayName("Coverage Tests")
    class CoverageTests {

        @Test
        @DisplayName("Should return 1.0 when all catalog items can be recommended")
        void shouldReturnOneWhenAllCatalogCanBeRecommended() {
            Set<Long> uniqueRecommended = new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L));
            int catalogSize = 5;

            double coverage = evaluator.calculateCoverage(uniqueRecommended, catalogSize);

            assertEquals(1.0, coverage, 0.001);
        }

        @Test
        @DisplayName("Should calculate coverage correctly for partial coverage")
        void shouldCalculateCoverageForPartialCoverage() {
            Set<Long> uniqueRecommended = new HashSet<>(Arrays.asList(1L, 2L, 3L));
            int catalogSize = 10;

            double coverage = evaluator.calculateCoverage(uniqueRecommended, catalogSize);

            assertEquals(0.3, coverage, 0.001);
        }

        @Test
        @DisplayName("Should handle zero catalog size")
        void shouldHandleZeroCatalogSize() {
            Set<Long> uniqueRecommended = new HashSet<>(Arrays.asList(1L, 2L, 3L));
            int catalogSize = 0;

            double coverage = evaluator.calculateCoverage(uniqueRecommended, catalogSize);

            assertEquals(0.0, coverage, 0.001);
        }

        @Test
        @DisplayName("Should handle empty unique recommended set")
        void shouldHandleEmptyUniqueRecommended() {
            Set<Long> uniqueRecommended = Collections.emptySet();
            int catalogSize = 10;

            double coverage = evaluator.calculateCoverage(uniqueRecommended, catalogSize);

            assertEquals(0.0, coverage, 0.001);
        }
    }

    @Nested
    @DisplayName("Diversity Tests")
    class DiversityTests {

        @Test
        @DisplayName("Should return 1.0 for completely different items")
        void shouldReturnOneForCompletelyDifferentItems() {
            List<RecommendItem> items = Arrays.asList(
                    createItemWithTags(1L, Set.of("array")),
                    createItemWithTags(2L, Set.of("tree")),
                    createItemWithTags(3L, Set.of("graph"))
            );

            double diversity = evaluator.calculateDiversity(items);

            // All items have completely different tags, so diversity should be 1.0
            assertEquals(1.0, diversity, 0.001);
        }

        @Test
        @DisplayName("Should return 0.0 for items with same tags")
        void shouldReturnZeroForItemsWithSameTags() {
            Set<String> sameTags = new HashSet<>(Arrays.asList("array", "hash-table"));
            List<RecommendItem> items = Arrays.asList(
                    createItemWithTags(1L, sameTags),
                    createItemWithTags(2L, sameTags),
                    createItemWithTags(3L, sameTags)
            );

            double diversity = evaluator.calculateDiversity(items);

            assertEquals(0.0, diversity, 0.001);
        }

        @Test
        @DisplayName("Should calculate diversity correctly for partially similar items")
        void shouldCalculateDiversityForPartiallySimilarItems() {
            List<RecommendItem> items = Arrays.asList(
                    createItemWithTags(1L, new HashSet<>(Arrays.asList("array", "hash-table"))),
                    createItemWithTags(2L, new HashSet<>(Arrays.asList("array", "two-pointers")))
            );

            // Tags1 = {array, hash-table}, Tags2 = {array, two-pointers}
            // Intersection = {array} = 1
            // Union = {array, hash-table, two-pointers} = 3
            // Jaccard similarity = 1/3
            // Jaccard distance = 1 - 1/3 = 2/3
            double diversity = evaluator.calculateDiversity(items);

            assertEquals(2.0 / 3.0, diversity, 0.001);
        }

        @Test
        @DisplayName("Should handle items with no tags")
        void shouldHandleItemsWithNoTags() {
            List<RecommendItem> items = Arrays.asList(
                    createItemWithTags(1L, Collections.emptySet()),
                    createItemWithTags(2L, Collections.emptySet())
            );

            double diversity = evaluator.calculateDiversity(items);

            // Empty tags are treated as identical (distance = 0)
            assertEquals(0.0, diversity, 0.001);
        }

        @Test
        @DisplayName("Should handle single item list")
        void shouldHandleSingleItemList() {
            List<RecommendItem> items = Collections.singletonList(
                    createItemWithTags(1L, Set.of("array"))
            );

            double diversity = evaluator.calculateDiversity(items);

            // No pairs to compare, diversity is 0 or undefined
            assertEquals(0.0, diversity, 0.001);
        }

        @Test
        @DisplayName("Should handle empty items list")
        void shouldHandleEmptyItemsList() {
            List<RecommendItem> items = Collections.emptyList();

            double diversity = evaluator.calculateDiversity(items);

            assertEquals(0.0, diversity, 0.001);
        }

        @Test
        @DisplayName("Should handle null tags")
        void shouldHandleNullTags() {
            List<RecommendItem> items = Arrays.asList(
                    createItemWithTags(1L, null),
                    createItemWithTags(2L, null)
            );

            double diversity = evaluator.calculateDiversity(items);

            // Null tags are treated as empty sets (identical)
            assertEquals(0.0, diversity, 0.001);
        }
    }

    @Nested
    @DisplayName("Full Evaluation Tests")
    class FullEvaluationTests {

        @Test
        @DisplayName("Should return complete OfflineMetrics")
        void shouldReturnCompleteOfflineMetrics() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L, 6L));
            List<RecommendItem> items = Arrays.asList(
                    createItemWithTags(1L, Set.of("array")),
                    createItemWithTags(2L, Set.of("tree")),
                    createItemWithTags(3L, Set.of("graph")),
                    createItemWithTags(4L, Set.of("dp")),
                    createItemWithTags(5L, Set.of("math"))
            );

            OfflineMetrics metrics = evaluator.evaluate(recommended, relevant, 5, items, 100);

            assertNotNull(metrics);
            assertTrue(metrics.getPrecision() >= 0 && metrics.getPrecision() <= 1);
            assertTrue(metrics.getRecall() >= 0 && metrics.getRecall() <= 1);
            assertTrue(metrics.getF1Score() >= 0 && metrics.getF1Score() <= 1);
            assertTrue(metrics.getNdcg() >= 0 && metrics.getNdcg() <= 1);
            assertTrue(metrics.getCoverage() >= 0 && metrics.getCoverage() <= 1);
            assertTrue(metrics.getDiversity() >= 0 && metrics.getDiversity() <= 1);
        }

        @Test
        @DisplayName("Should include K in metrics")
        void shouldIncludeKInMetrics() {
            List<Long> recommended = Arrays.asList(1L, 2L, 3L);
            Set<Long> relevant = new HashSet<>(Arrays.asList(1L, 2L));

            OfflineMetrics metrics = evaluator.evaluate(recommended, relevant, 10, null, 0);

            assertEquals(10, metrics.getK());
        }
    }

    @Nested
    @DisplayName("Aggregate Evaluation Tests")
    class AggregateEvaluationTests {

        @Test
        @DisplayName("Should aggregate metrics from multiple evaluations")
        void shouldAggregateMetricsFromMultipleEvaluations() {
            // Evaluation 1: perfect
            List<Long> rec1 = Arrays.asList(1L, 2L, 3L);
            Set<Long> rel1 = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            // Evaluation 2: no overlap
            List<Long> rec2 = Arrays.asList(4L, 5L, 6L);
            Set<Long> rel2 = new HashSet<>(Arrays.asList(1L, 2L, 3L));

            // Evaluation 3: partial
            List<Long> rec3 = Arrays.asList(1L, 2L, 3L);
            Set<Long> rel3 = new HashSet<>(Arrays.asList(1L, 4L, 5L));

            List<EvaluationInput> inputs = Arrays.asList(
                    new EvaluationInput(rec1, rel1, 3, Collections.emptyList(), 100),
                    new EvaluationInput(rec2, rel2, 3, Collections.emptyList(), 100),
                    new EvaluationInput(rec3, rel3, 3, Collections.emptyList(), 100)
            );

            OfflineMetrics aggregate = evaluator.evaluateAggregate(inputs);

            // Evaluation 3: rec3 = [1, 2, 3], rel3 = {1, 4, 5}
            // Precision@3 = 1/3 = 0.333... (only item 1 is relevant)
            // Recall@3 = 1/3 = 0.333... (1 out of 3 relevant items found)
            // Average precision: (1.0 + 0.0 + 0.333...) / 3 = 0.444...
            assertEquals((1.0 + 0.0 + 1.0 / 3.0) / 3.0, aggregate.getPrecision(), 0.01);
            // Average recall: (1.0 + 0.0 + 0.333...) / 3 = 0.444...
            assertEquals((1.0 + 0.0 + 1.0 / 3.0) / 3.0, aggregate.getRecall(), 0.01);
        }

        @Test
        @DisplayName("Should handle empty input list for aggregation")
        void shouldHandleEmptyInputListForAggregation() {
            List<EvaluationInput> inputs = Collections.emptyList();

            OfflineMetrics aggregate = evaluator.evaluateAggregate(inputs);

            assertNotNull(aggregate);
            assertEquals(0.0, aggregate.getPrecision(), 0.001);
            assertEquals(0.0, aggregate.getRecall(), 0.001);
            assertEquals(0.0, aggregate.getF1Score(), 0.001);
        }

        @Test
        @DisplayName("Should aggregate coverage correctly")
        void shouldAggregateCoverageCorrectly() {
            List<Long> rec1 = Arrays.asList(1L, 2L, 3L);
            List<Long> rec2 = Arrays.asList(2L, 3L, 4L);
            List<Long> rec3 = Arrays.asList(4L, 5L, 6L);

            List<EvaluationInput> inputs = Arrays.asList(
                    new EvaluationInput(rec1, Collections.emptySet(), 3, Collections.emptyList(), 10),
                    new EvaluationInput(rec2, Collections.emptySet(), 3, Collections.emptyList(), 10),
                    new EvaluationInput(rec3, Collections.emptySet(), 3, Collections.emptyList(), 10)
            );

            OfflineMetrics aggregate = evaluator.evaluateAggregate(inputs);

            // Unique items across all recommendations: {1, 2, 3, 4, 5, 6} = 6
            // Catalog size = 10
            // Coverage = 6/10 = 0.6
            assertEquals(0.6, aggregate.getCoverage(), 0.001);
        }
    }

    @Nested
    @DisplayName("OfflineMetrics Tests")
    class OfflineMetricsClassTests {

        @Test
        @DisplayName("Should build OfflineMetrics using builder")
        void shouldBuildOfflineMetricsUsingBuilder() {
            OfflineMetrics metrics = OfflineMetrics.builder()
                    .k(10)
                    .precision(0.8)
                    .recall(0.6)
                    .f1Score(0.686)
                    .ndcg(0.75)
                    .coverage(0.5)
                    .diversity(0.9)
                    .build();

            assertEquals(10, metrics.getK());
            assertEquals(0.8, metrics.getPrecision(), 0.001);
            assertEquals(0.6, metrics.getRecall(), 0.001);
            assertEquals(0.686, metrics.getF1Score(), 0.001);
            assertEquals(0.75, metrics.getNdcg(), 0.001);
            assertEquals(0.5, metrics.getCoverage(), 0.001);
            assertEquals(0.9, metrics.getDiversity(), 0.001);
        }

        @Test
        @DisplayName("Should have meaningful toString output")
        void shouldHaveMeaningfulToString() {
            OfflineMetrics metrics = OfflineMetrics.builder()
                    .k(10)
                    .precision(0.8)
                    .recall(0.6)
                    .f1Score(0.686)
                    .ndcg(0.75)
                    .coverage(0.5)
                    .diversity(0.9)
                    .build();

            String str = metrics.toString();

            assertTrue(str.contains("precision"));
            assertTrue(str.contains("recall"));
            assertTrue(str.contains("f1Score"));
            assertTrue(str.contains("ndcg"));
            assertTrue(str.contains("coverage"));
            assertTrue(str.contains("diversity"));
        }
    }

    // Helper method to create RecommendItem with specific tags
    private RecommendItem createItemWithTags(Long problemId, Set<String> tags) {
        return RecommendItem.builder()
                .problemId(problemId)
                .tags(tags)
                .build();
    }
}

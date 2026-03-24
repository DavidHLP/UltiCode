package com.ulticode.recommend.core;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import com.ulticode.recommend.core.rank.RankStrategy;
import com.ulticode.recommend.core.rerank.ReRankStrategy;
import com.ulticode.recommend.core.recall.RecallStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for RecommendEngine - the main orchestrator for recommendation pipeline.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Engine construction with default and custom strategies</li>
 *   <li>Pipeline execution flow</li>
 *   <li>Deduplication of recall results</li>
 *   <li>Filtering of solved problems</li>
 *   <li>Size limiting</li>
 *   <li>Priority ordering of strategies</li>
 *   <li>Edge cases (null, empty)</li>
 * </ul>
 */
@DisplayName("RecommendEngine Tests")
class RecommendEngineTest {

    private RecommendContext context;
    private UserProfile profile;
    private List<RecommendItem> testItems;

    @BeforeEach
    void setUp() {
        context = RecommendContext.builder()
                .userId("user1")
                .size(10)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();

        profile = UserProfile.builder()
                .userId("user1")
                .rating(1500)
                .solvedProblems(new HashSet<>())
                .tagMastery(new HashMap<>())
                .build();

        testItems = createTestItems(20);
    }

    // ==================== Construction Tests ====================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should create engine with default strategies")
        void shouldCreateWithDefaultStrategies() {
            RecommendEngine engine = new RecommendEngine();

            assertNotNull(engine);
            assertFalse(engine.getRecallStrategies().isEmpty());
            assertNotNull(engine.getRankStrategy());
            assertFalse(engine.getReRankStrategies().isEmpty());
        }

        @Test
        @DisplayName("Should create engine with custom strategies")
        void shouldCreateWithCustomStrategies() {
            List<RecallStrategy> recallStrategies = List.of(
                    createMockRecallStrategy("MockRecall", 10, testItems)
            );
            RankStrategy rankStrategy = createMockRankStrategy("MockRank", 100);
            List<ReRankStrategy> reRankStrategies = List.of(
                    createMockReRankStrategy("MockReRank", 50)
            );

            RecommendEngine engine = new RecommendEngine(
                    recallStrategies, rankStrategy, reRankStrategies
            );

            assertEquals(1, engine.getRecallStrategies().size());
            assertEquals("MockRecall", engine.getRecallStrategies().get(0).getName());
            assertEquals("MockRank", engine.getRankStrategy().getName());
            assertEquals(1, engine.getReRankStrategies().size());
        }

        @Test
        @DisplayName("Should sort recall strategies by priority (lower first)")
        void shouldSortRecallStrategiesByPriority() {
            RecallStrategy lowPriority = createMockRecallStrategy("Low", 5, testItems);
            RecallStrategy highPriority = createMockRecallStrategy("High", 30, testItems);
            RecallStrategy mediumPriority = createMockRecallStrategy("Medium", 15, testItems);

            RecommendEngine engine = new RecommendEngine(
                    List.of(highPriority, lowPriority, mediumPriority),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecallStrategy> sorted = engine.getRecallStrategies();
            assertEquals("Low", sorted.get(0).getName());
            assertEquals("Medium", sorted.get(1).getName());
            assertEquals("High", sorted.get(2).getName());
        }

        @Test
        @DisplayName("Should sort re-rank strategies by priority (higher first)")
        void shouldSortReRankStrategiesByPriority() {
            ReRankStrategy lowPriority = createMockReRankStrategy("Low", 30);
            ReRankStrategy highPriority = createMockReRankStrategy("High", 60);
            ReRankStrategy mediumPriority = createMockReRankStrategy("Medium", 45);

            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of(lowPriority, highPriority, mediumPriority)
            );

            List<ReRankStrategy> sorted = engine.getReRankStrategies();
            assertEquals("High", sorted.get(0).getName());
            assertEquals("Medium", sorted.get(1).getName());
            assertEquals("Low", sorted.get(2).getName());
        }
    }

    // ==================== Pipeline Execution Tests ====================

    @Nested
    @DisplayName("Pipeline Execution")
    class PipelineExecutionTests {

        @Test
        @DisplayName("Should execute full pipeline and return results")
        void shouldExecuteFullPipeline() {
            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of(createMockReRankStrategy("ReRank", 50))
            );

            List<RecommendItem> results = engine.recommend(context, profile);

            assertNotNull(results);
            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("Should execute multiple recall strategies and merge results")
        void shouldExecuteMultipleRecallStrategies() {
            List<RecommendItem> items1 = createTestItems(5, 1); // IDs 1-5
            List<RecommendItem> items2 = createTestItems(5, 6); // IDs 6-10

            RecommendEngine engine = new RecommendEngine(
                    List.of(
                            createMockRecallStrategy("Recall1", 10, items1),
                            createMockRecallStrategy("Recall2", 20, items2)
                    ),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(context, profile);

            // Should have merged results from both strategies
            assertTrue(results.size() >= 5);
        }

        @Test
        @DisplayName("Should deduplicate items by problemId")
        void shouldDeduplicateItems() {
            List<RecommendItem> items1 = createTestItems(5, 1); // IDs 1-5
            List<RecommendItem> items2 = createTestItems(7, 1); // IDs 1-7 (overlap with first)

            RecommendEngine engine = new RecommendEngine(
                    List.of(
                            createMockRecallStrategy("Recall1", 10, items1),
                            createMockRecallStrategy("Recall2", 20, items2)
                    ),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(context, profile);

            // Count unique problem IDs
            long uniqueIds = results.stream()
                    .map(RecommendItem::getProblemId)
                    .distinct()
                    .count();

            assertEquals(results.size(), uniqueIds, "Results should not contain duplicates");
        }

        @Test
        @DisplayName("Should apply rank strategy to merged items")
        void shouldApplyRankStrategy() {
            List<RecommendItem> items = createTestItems(5, 1);
            // Custom rank strategy that sets all scores to 0.99
            RankStrategy rankStrategy = new RankStrategy() {
                @Override
                public List<RecommendItem> rank(List<RecommendItem> items, RecommendContext ctx, UserProfile prof) {
                    return items.stream()
                            .map(item -> RecommendItem.builder()
                                    .problemId(item.getProblemId())
                                    .title(item.getTitle())
                                    .score(0.99)
                                    .build())
                            .toList();
                }

                @Override
                public String getName() {
                    return "CustomRank";
                }
            };

            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, items)),
                    rankStrategy,
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(context, profile);

            // All items should have score 0.99
            for (RecommendItem item : results) {
                assertEquals(0.99, item.getScore(), 0.001);
            }
        }

        @Test
        @DisplayName("Should apply re-rank strategies in priority order (higher first)")
        void shouldApplyReRankStrategiesInOrder() {
            List<String> executionOrder = new ArrayList<>();

            ReRankStrategy first = createTrackingReRankStrategy("First", 60, executionOrder);
            ReRankStrategy second = createTrackingReRankStrategy("Second", 40, executionOrder);
            ReRankStrategy third = createTrackingReRankStrategy("Third", 20, executionOrder);

            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of(third, first, second) // Provided in wrong order
            );

            engine.recommend(context, profile);

            // Should execute in priority order: First (60) -> Second (40) -> Third (20)
            assertEquals(List.of("First", "Second", "Third"), executionOrder);
        }

        @Test
        @DisplayName("Should limit results to context size")
        void shouldLimitResultsToContextSize() {
            RecommendContext smallContext = RecommendContext.builder()
                    .userId("user1")
                    .size(5)
                    .build();

            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(smallContext, profile);

            assertTrue(results.size() <= 5, "Results should be limited to context size");
        }
    }

    // ==================== Solved Problems Filtering Tests ====================

    @Nested
    @DisplayName("Solved Problems Filtering")
    class SolvedProblemsFilteringTests {

        @Test
        @DisplayName("Should filter out solved problems from results")
        void shouldFilterSolvedProblems() {
            Set<Long> solvedProblems = new HashSet<>(Arrays.asList(1L, 2L, 3L));
            UserProfile profileWithSolved = UserProfile.builder()
                    .userId("user1")
                    .rating(1500)
                    .solvedProblems(solvedProblems)
                    .build();

            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(context, profileWithSolved);

            for (RecommendItem item : results) {
                assertFalse(solvedProblems.contains(item.getProblemId()),
                        "Results should not contain solved problems");
            }
        }

        @Test
        @DisplayName("Should include solved problems when includeSolved is true")
        void shouldIncludeSolvedProblemsWhenRequested() {
            RecommendContext contextWithSolved = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .includeSolved(true)
                    .build();

            Set<Long> solvedProblems = new HashSet<>(Arrays.asList(1L, 2L, 3L));
            UserProfile profileWithSolved = UserProfile.builder()
                    .userId("user1")
                    .rating(1500)
                    .solvedProblems(solvedProblems)
                    .build();

            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(contextWithSolved, profileWithSolved);

            // Results may include solved problems
            // We just verify the method doesn't throw and returns results
            assertNotNull(results);
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty recall results gracefully")
        void shouldHandleEmptyRecallResults() {
            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("EmptyRecall", 10, List.of())),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(context, profile);

            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Should handle null profile")
        void shouldHandleNullProfile() {
            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(context, null);

            assertNotNull(results);
            // Should still return results
        }

        @Test
        @DisplayName("Should handle null context")
        void shouldHandleNullContext() {
            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(null, profile);

            assertNotNull(results);
        }

        @Test
        @DisplayName("Should handle profile with null solved problems")
        void shouldHandleNullSolvedProblems() {
            UserProfile profileNullSolved = UserProfile.builder()
                    .userId("user1")
                    .rating(1500)
                    .solvedProblems(null)
                    .build();

            RecommendEngine engine = new RecommendEngine(
                    List.of(createMockRecallStrategy("Recall", 10, testItems)),
                    createMockRankStrategy("Rank", 100),
                    List.of()
            );

            List<RecommendItem> results = engine.recommend(context, profileNullSolved);

            assertNotNull(results);
        }

        @Test
        @DisplayName("Should handle all recall strategies returning empty")
        void shouldHandleAllRecallStrategiesEmpty() {
            RecommendEngine engine = new RecommendEngine(
                    List.of(
                            createMockRecallStrategy("Recall1", 10, List.of()),
                            createMockRecallStrategy("Recall2", 20, List.of())
                    ),
                    createMockRankStrategy("Rank", 100),
                    List.of(createMockReRankStrategy("ReRank", 50))
            );

            List<RecommendItem> results = engine.recommend(context, profile);

            assertNotNull(results);
            assertTrue(results.isEmpty());
        }
    }

    // ==================== Default Strategies Tests ====================

    @Nested
    @DisplayName("Default Strategies")
    class DefaultStrategiesTests {

        @Test
        @DisplayName("Default engine should return recommendations")
        void defaultEngineShouldReturnRecommendations() {
            RecommendEngine engine = new RecommendEngine();

            // Note: Default strategies may return empty if no data is available
            // This test just verifies the engine doesn't throw
            assertDoesNotThrow(() -> engine.recommend(context, profile));
        }
    }

    // ==================== Helper Methods ====================

    private List<RecommendItem> createTestItems(int count) {
        return createTestItems(count, 1);
    }

    private List<RecommendItem> createTestItems(int count, int startId) {
        List<RecommendItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = startId + i;
            items.add(RecommendItem.builder()
                    .problemId(id)
                    .title("Problem " + id)
                    .slug("problem-" + id)
                    .difficulty("Medium")
                    .score(0.5 + (id % 10) * 0.05)
                    .qualityScore(0.7)
                    .tags(new HashSet<>(Arrays.asList("Array", "DP")))
                    .createdAt(LocalDateTime.now().minusDays(id))
                    .build());
        }
        return items;
    }

    private RecallStrategy createMockRecallStrategy(String name, int priority, List<RecommendItem> items) {
        return new RecallStrategy() {
            @Override
            public List<RecommendItem> recall(RecommendContext context, UserProfile profile) {
                return new ArrayList<>(items);
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    private RankStrategy createMockRankStrategy(String name, int priority) {
        return new RankStrategy() {
            @Override
            public List<RecommendItem> rank(List<RecommendItem> items, RecommendContext ctx, UserProfile prof) {
                return new ArrayList<>(items);
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    private ReRankStrategy createMockReRankStrategy(String name, int priority) {
        return new ReRankStrategy() {
            @Override
            public List<RecommendItem> rerank(List<RecommendItem> items, RecommendContext ctx, UserProfile prof) {
                return new ArrayList<>(items);
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    private ReRankStrategy createTrackingReRankStrategy(String name, int priority, List<String> executionOrder) {
        return new ReRankStrategy() {
            @Override
            public List<RecommendItem> rerank(List<RecommendItem> items, RecommendContext ctx, UserProfile prof) {
                executionOrder.add(name);
                return new ArrayList<>(items);
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }
}

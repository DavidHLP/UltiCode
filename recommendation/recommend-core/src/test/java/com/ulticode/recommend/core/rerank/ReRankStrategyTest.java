package com.ulticode.recommend.core.rerank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReRankStrategy interface.
 */
@DisplayName("ReRankStrategy Interface Tests")
class ReRankStrategyTest {

    private RecommendContext context;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        context = RecommendContext.builder()
                .userId("test-user")
                .size(10)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();

        profile = UserProfile.builder()
                .userId("test-user")
                .rating(1500)
                .build();
    }

    @Test
    @DisplayName("Default getName() returns class simple name")
    void defaultGetNameReturnsClassSimpleName() {
        // Create a named implementation
        class TestReRankStrategy implements ReRankStrategy {
            @Override
            public List<RecommendItem> rerank(List<RecommendItem> items,
                                              RecommendContext context,
                                              UserProfile profile) {
                return items;
            }
        }

        ReRankStrategy strategy = new TestReRankStrategy();
        assertEquals("TestReRankStrategy", strategy.getName());
    }

    @Test
    @DisplayName("Default getPriority() returns 0")
    void defaultGetPriorityReturnsZero() {
        ReRankStrategy strategy = (items, ctx, prof) -> items;

        assertEquals(0, strategy.getPriority());
    }

    @Test
    @DisplayName("Rerank with simple implementation returns items")
    void rerankWithSimpleImplementationReturnsItems() {
        RecommendItem item1 = RecommendItem.builder()
                .problemId(1L)
                .score(0.9)
                .build();

        RecommendItem item2 = RecommendItem.builder()
                .problemId(2L)
                .score(0.8)
                .build();

        List<RecommendItem> items = Arrays.asList(item1, item2);

        // Simple strategy that just returns items unchanged
        ReRankStrategy strategy = (itemList, ctx, prof) -> itemList;

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(0.9, result.get(0).getScore(), 0.001);
        assertEquals(0.8, result.get(1).getScore(), 0.001);
    }

    @Test
    @DisplayName("Rerank works with lambda implementation")
    void rerankWorksWithLambdaImplementation() {
        RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .score(0.5)
                .build();

        List<RecommendItem> items = Collections.singletonList(item);

        ReRankStrategy lambdaStrategy = (itemList, ctx, prof) -> itemList;

        List<RecommendItem> result = lambdaStrategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Rerank handles empty list")
    void rerankHandlesEmptyList() {
        List<RecommendItem> emptyItems = Collections.emptyList();

        ReRankStrategy strategy = (items, ctx, prof) -> items;

        List<RecommendItem> result = strategy.rerank(emptyItems, context, profile);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Rerank can modify scores")
    void rerankCanModifyScores() {
        RecommendItem item1 = RecommendItem.builder()
                .problemId(1L)
                .score(0.9)
                .build();

        RecommendItem item2 = RecommendItem.builder()
                .problemId(2L)
                .score(0.8)
                .build();

        List<RecommendItem> items = Arrays.asList(item1, item2);

        // Strategy that applies a boost multiplier to scores
        ReRankStrategy boostStrategy = (itemList, ctx, prof) -> {
            for (RecommendItem item : itemList) {
                item.setScore(item.getScore() * 1.1); // 10% boost
            }
            return itemList;
        };

        List<RecommendItem> result = boostStrategy.rerank(items, context, profile);

        assertEquals(2, result.size());
        assertEquals(0.99, result.get(0).getScore(), 0.001); // 0.9 * 1.1
        assertEquals(0.88, result.get(1).getScore(), 0.001); // 0.8 * 1.1
    }

    @Test
    @DisplayName("Strategy with custom priority")
    void strategyWithCustomPriority() {
        ReRankStrategy highPriorityStrategy = new ReRankStrategy() {
            @Override
            public List<RecommendItem> rerank(List<RecommendItem> items,
                                              RecommendContext context,
                                              UserProfile profile) {
                return items;
            }

            @Override
            public int getPriority() {
                return 100;
            }
        };

        assertEquals(100, highPriorityStrategy.getPriority());
    }

    @Test
    @DisplayName("Strategy with custom name")
    void strategyWithCustomName() {
        ReRankStrategy namedStrategy = new ReRankStrategy() {
            @Override
            public List<RecommendItem> rerank(List<RecommendItem> items,
                                              RecommendContext context,
                                              UserProfile profile) {
                return items;
            }

            @Override
            public String getName() {
                return "DiversityReRanker";
            }
        };

        assertEquals("DiversityReRanker", namedStrategy.getName());
    }

    @Test
    @DisplayName("Multiple strategies sorted by priority")
    void multipleStrategiesSortedByPriority() {
        ReRankStrategy lowPriority = new ReRankStrategy() {
            @Override
            public List<RecommendItem> rerank(List<RecommendItem> items,
                                              RecommendContext context,
                                              UserProfile profile) {
                return items;
            }
            @Override
            public int getPriority() { return 10; }
        };

        ReRankStrategy highPriority = new ReRankStrategy() {
            @Override
            public List<RecommendItem> rerank(List<RecommendItem> items,
                                              RecommendContext context,
                                              UserProfile profile) {
                return items;
            }
            @Override
            public int getPriority() { return 100; }
        };

        ReRankStrategy defaultPriority = (items, ctx, prof) -> items;

        List<ReRankStrategy> strategies = Arrays.asList(
                lowPriority, highPriority, defaultPriority
        );

        strategies.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        assertEquals(100, strategies.get(0).getPriority());
        assertEquals(10, strategies.get(1).getPriority());
        assertEquals(0, strategies.get(2).getPriority());
    }

    @Test
    @DisplayName("Rerank can reorder items for diversity")
    void rerankCanReorderItemsForDiversity() {
        // Items with same tag (low diversity)
        RecommendItem item1 = RecommendItem.builder()
                .problemId(1L)
                .title("Two Sum")
                .difficulty("Easy")
                .score(0.9)
                .build();

        RecommendItem item2 = RecommendItem.builder()
                .problemId(2L)
                .title("Add Two Numbers")
                .difficulty("Easy")
                .score(0.85)
                .build();

        RecommendItem item3 = RecommendItem.builder()
                .problemId(3L)
                .title("Binary Tree Traversal")
                .difficulty("Medium")
                .score(0.8)
                .build();

        List<RecommendItem> items = Arrays.asList(item1, item2, item3);

        // Strategy that ensures diversity by alternating difficulties
        ReRankStrategy diversityStrategy = (itemList, ctx, prof) -> {
            // Simple diversity: move Medium difficulty item up
            itemList.sort((a, b) -> {
                // Give Medium a boost to ensure diversity
                if (a.getDifficulty().equals("Medium") && !b.getDifficulty().equals("Medium")) {
                    return -1;
                }
                if (!a.getDifficulty().equals("Medium") && b.getDifficulty().equals("Medium")) {
                    return 1;
                }
                return Double.compare(b.getScore(), a.getScore());
            });
            return itemList;
        };

        List<RecommendItem> result = diversityStrategy.rerank(items, context, profile);

        assertEquals(3, result.size());
        // Medium difficulty should be first for diversity
        assertEquals("Medium", result.get(0).getDifficulty());
    }

    @Test
    @DisplayName("Rerank preserves item properties after modification")
    void rerankPreservesItemPropertiesAfterModification() {
        RecommendItem item1 = RecommendItem.builder()
                .problemId(1L)
                .slug("two-sum")
                .title("Two Sum")
                .difficulty("Easy")
                .score(0.8)
                .reason("Based on your history")
                .build();

        RecommendItem item2 = RecommendItem.builder()
                .problemId(2L)
                .slug("add-two-numbers")
                .title("Add Two Numbers")
                .difficulty("Medium")
                .score(0.6)
                .build();

        List<RecommendItem> items = Arrays.asList(item2, item1);

        ReRankStrategy strategy = (itemList, ctx, prof) -> {
            // Boost score and set reason
            for (RecommendItem item : itemList) {
                if (item.getReason() == null) {
                    item.setReason("Re-ranked for freshness");
                }
            }
            itemList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return itemList;
        };

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(2, result.size());

        // First item should be item1 (higher score)
        RecommendItem first = result.get(0);
        assertEquals(1L, first.getProblemId());
        assertEquals("two-sum", first.getSlug());
        assertEquals("Two Sum", first.getTitle());
        assertEquals("Easy", first.getDifficulty());
        assertEquals(0.8, first.getScore(), 0.001);
        assertEquals("Based on your history", first.getReason());

        // Second item should have new reason
        RecommendItem second = result.get(1);
        assertEquals("Re-ranked for freshness", second.getReason());
    }
}

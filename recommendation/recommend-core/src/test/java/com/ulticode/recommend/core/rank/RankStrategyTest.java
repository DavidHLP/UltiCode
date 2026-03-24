package com.ulticode.recommend.core.rank;

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
 * Tests for RankStrategy interface.
 */
@DisplayName("RankStrategy Interface Tests")
class RankStrategyTest {

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
        class TestRankStrategy implements RankStrategy {
            @Override
            public List<RecommendItem> rank(List<RecommendItem> items,
                                           RecommendContext context,
                                           UserProfile profile) {
                return items;
            }
        }

        RankStrategy strategy = new TestRankStrategy();
        assertEquals("TestRankStrategy", strategy.getName());
    }

    @Test
    @DisplayName("Default getPriority() returns 0")
    void defaultGetPriorityReturnsZero() {
        RankStrategy strategy = (items, ctx, prof) -> items;

        assertEquals(0, strategy.getPriority());
    }

    @Test
    @DisplayName("Rank sorts items by score descending")
    void rankSortsItemsByScoreDescending() {
        // Create items with different scores
        RecommendItem item1 = RecommendItem.builder()
                .problemId(1L)
                .score(0.5)
                .build();

        RecommendItem item2 = RecommendItem.builder()
                .problemId(2L)
                .score(0.9)
                .build();

        RecommendItem item3 = RecommendItem.builder()
                .problemId(3L)
                .score(0.7)
                .build();

        List<RecommendItem> items = Arrays.asList(item1, item2, item3);

        // Strategy that sorts by score descending
        RankStrategy strategy = (itemList, ctx, prof) -> {
            itemList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return itemList;
        };

        List<RecommendItem> ranked = strategy.rank(items, context, profile);

        assertEquals(3, ranked.size());
        assertEquals(0.9, ranked.get(0).getScore(), 0.001);
        assertEquals(0.7, ranked.get(1).getScore(), 0.001);
        assertEquals(0.5, ranked.get(2).getScore(), 0.001);
    }

    @Test
    @DisplayName("Rank works with lambda implementation")
    void rankWorksWithLambdaImplementation() {
        RecommendItem item = RecommendItem.builder()
                .problemId(1L)
                .score(0.5)
                .build();

        List<RecommendItem> items = Collections.singletonList(item);

        RankStrategy lambdaStrategy = (itemList, ctx, prof) -> itemList;

        List<RecommendItem> result = lambdaStrategy.rank(items, context, profile);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Rank handles empty list")
    void rankHandlesEmptyList() {
        List<RecommendItem> emptyItems = Collections.emptyList();

        RankStrategy strategy = (items, ctx, prof) -> items;

        List<RecommendItem> result = strategy.rank(emptyItems, context, profile);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Rank handles null-safe scoring")
    void rankHandlesNullSafeScoring() {
        RecommendItem itemWithNullScore = RecommendItem.builder()
                .problemId(1L)
                .build();

        // Default score should be 0.0
        assertEquals(0.0, itemWithNullScore.getScore(), 0.001);

        List<RecommendItem> items = Collections.singletonList(itemWithNullScore);

        RankStrategy strategy = (itemList, ctx, prof) -> itemList;

        List<RecommendItem> result = strategy.rank(items, context, profile);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Strategy with custom priority")
    void strategyWithCustomPriority() {
        RankStrategy highPriorityStrategy = new RankStrategy() {
            @Override
            public List<RecommendItem> rank(List<RecommendItem> items,
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
        RankStrategy namedStrategy = new RankStrategy() {
            @Override
            public List<RecommendItem> rank(List<RecommendItem> items,
                                           RecommendContext context,
                                           UserProfile profile) {
                return items;
            }

            @Override
            public String getName() {
                return "CustomRankStrategy";
            }
        };

        assertEquals("CustomRankStrategy", namedStrategy.getName());
    }

    @Test
    @DisplayName("Multiple strategies sorted by priority")
    void multipleStrategiesSortedByPriority() {
        RankStrategy lowPriority = new RankStrategy() {
            @Override
            public List<RecommendItem> rank(List<RecommendItem> items,
                                           RecommendContext context,
                                           UserProfile profile) {
                return items;
            }
            @Override
            public int getPriority() { return 10; }
        };

        RankStrategy highPriority = new RankStrategy() {
            @Override
            public List<RecommendItem> rank(List<RecommendItem> items,
                                           RecommendContext context,
                                           UserProfile profile) {
                return items;
            }
            @Override
            public int getPriority() { return 100; }
        };

        RankStrategy defaultPriority = (items, ctx, prof) -> items;

        List<RankStrategy> strategies = Arrays.asList(
                lowPriority, highPriority, defaultPriority
        );

        strategies.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        assertEquals(100, strategies.get(0).getPriority());
        assertEquals(10, strategies.get(1).getPriority());
        assertEquals(0, strategies.get(2).getPriority());
    }

    @Test
    @DisplayName("Rank preserves item properties after sorting")
    void rankPreservesItemPropertiesAfterSorting() {
        RecommendItem item1 = RecommendItem.builder()
                .problemId(1L)
                .slug("two-sum")
                .title("Two Sum")
                .difficulty("Easy")
                .score(0.8)
                .build();

        RecommendItem item2 = RecommendItem.builder()
                .problemId(2L)
                .slug("add-two-numbers")
                .title("Add Two Numbers")
                .difficulty("Medium")
                .score(0.6)
                .build();

        List<RecommendItem> items = Arrays.asList(item2, item1);

        RankStrategy strategy = (itemList, ctx, prof) -> {
            itemList.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return itemList;
        };

        List<RecommendItem> ranked = strategy.rank(items, context, profile);

        assertEquals(2, ranked.size());

        // First item should be item1 (higher score)
        RecommendItem first = ranked.get(0);
        assertEquals(1L, first.getProblemId());
        assertEquals("two-sum", first.getSlug());
        assertEquals("Two Sum", first.getTitle());
        assertEquals("Easy", first.getDifficulty());
        assertEquals(0.8, first.getScore(), 0.001);
    }
}

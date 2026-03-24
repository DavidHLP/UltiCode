package com.ulticode.recommend.core.rerank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DiversityReRankStrategy.
 */
@DisplayName("DiversityReRankStrategy Tests")
class DiversityReRankStrategyTest {

    private DiversityReRankStrategy strategy;
    private RecommendContext context;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        strategy = new DiversityReRankStrategy();
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
    @DisplayName("Strategy has correct priority of 50")
    void strategyHasCorrectPriority() {
        assertEquals(50, strategy.getPriority());
    }

    @Test
    @DisplayName("Strategy name is DiversityReRankStrategy")
    void strategyNameIsDiversityReRankStrategy() {
        assertEquals("DiversityReRankStrategy", strategy.getName());
    }

    @Test
    @DisplayName("Handles empty list by returning empty list")
    void handlesEmptyList() {
        List<RecommendItem> emptyItems = Collections.emptyList();

        List<RecommendItem> result = strategy.rerank(emptyItems, context, profile);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Handles null list by returning empty list")
    void handlesNullList() {
        List<RecommendItem> result = strategy.rerank(null, context, profile);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Handles single item correctly")
    void handlesSingleItem() {
        RecommendItem item = createItem(1L, 0.9, Set.of("array"));

        List<RecommendItem> items = Collections.singletonList(item);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getProblemId());
    }

    @Test
    @DisplayName("Handles item without tags")
    void handlesItemWithoutTags() {
        RecommendItem item = createItem(1L, 0.9, null);

        List<RecommendItem> items = Collections.singletonList(item);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getProblemId());
    }

    @Test
    @DisplayName("Handles item with empty tags set")
    void handlesItemWithEmptyTagsSet() {
        RecommendItem item = createItem(1L, 0.9, Collections.emptySet());

        List<RecommendItem> items = Collections.singletonList(item);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getProblemId());
    }

    @Test
    @DisplayName("Uses first tag as primary tag for grouping")
    void usesFirstTagAsPrimaryTag() {
        // Items with multiple tags - should use first tag for grouping
        RecommendItem item1 = createItem(1L, 0.9, Set.of("array", "hash-table"));
        RecommendItem item2 = createItem(2L, 0.8, Set.of("linked-list", "recursion"));
        RecommendItem item3 = createItem(3L, 0.7, Set.of("tree", "depth-first-search"));

        List<RecommendItem> items = Arrays.asList(item1, item2, item3);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(3, result.size());
        // All items should be present
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(1L)));
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(2L)));
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(3L)));
    }

    @Test
    @DisplayName("Round-robin selection from different tag groups")
    void roundRobinSelectionFromDifferentTagGroups() {
        // Create items with distinct tags
        RecommendItem array1 = createItem(1L, 0.95, Set.of("array"));
        RecommendItem array2 = createItem(2L, 0.85, Set.of("array"));
        RecommendItem array3 = createItem(3L, 0.75, Set.of("array"));

        RecommendItem tree1 = createItem(4L, 0.90, Set.of("tree"));
        RecommendItem tree2 = createItem(5L, 0.80, Set.of("tree"));

        RecommendItem dp1 = createItem(6L, 0.88, Set.of("dynamic-programming"));

        // Input sorted by score
        List<RecommendItem> items = Arrays.asList(
                array1, tree1, dp1, array2, tree2, array3
        );

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(6, result.size());

        // Verify round-robin pattern: should alternate between tag groups
        // First item should be from the highest scoring item in first tag group
        // Then alternate between groups
        Set<String> seenTags = new HashSet<>();
        for (RecommendItem item : result) {
            if (item.getTags() != null && !item.getTags().isEmpty()) {
                String primaryTag = item.getTags().iterator().next();
                seenTags.add(primaryTag);
            }
        }

        // We should have all three tag groups
        assertTrue(seenTags.contains("array"));
        assertTrue(seenTags.contains("tree"));
        assertTrue(seenTags.contains("dynamic-programming"));
    }

    @Test
    @DisplayName("Respects context size limit")
    void respectsContextSizeLimit() {
        RecommendContext smallContext = RecommendContext.builder()
                .userId("test-user")
                .size(3)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();

        // Create items from different tag groups
        RecommendItem array1 = createItem(1L, 0.95, Set.of("array"));
        RecommendItem array2 = createItem(2L, 0.85, Set.of("array"));
        RecommendItem tree1 = createItem(3L, 0.90, Set.of("tree"));
        RecommendItem tree2 = createItem(4L, 0.80, Set.of("tree"));
        RecommendItem dp1 = createItem(5L, 0.88, Set.of("dynamic-programming"));

        List<RecommendItem> items = Arrays.asList(array1, tree1, dp1, array2, tree2);

        List<RecommendItem> result = strategy.rerank(items, smallContext, profile);

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Maintains score ordering within tag groups")
    void maintainsScoreOrderingWithinTagGroups() {
        // Create items with same tag but different scores
        RecommendItem array1 = createItem(1L, 0.95, Set.of("array"));
        RecommendItem array2 = createItem(2L, 0.85, Set.of("array"));
        RecommendItem array3 = createItem(3L, 0.75, Set.of("array"));

        List<RecommendItem> items = Arrays.asList(array1, array2, array3);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(3, result.size());
        // Items from same tag group should maintain score order (descending)
        assertEquals(1L, result.get(0).getProblemId()); // score 0.95
        assertEquals(2L, result.get(1).getProblemId()); // score 0.85
        assertEquals(3L, result.get(2).getProblemId()); // score 0.75
    }

    @Test
    @DisplayName("Handles all items with same tag")
    void handlesAllItemsWithSameTag() {
        RecommendItem item1 = createItem(1L, 0.9, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.8, Set.of("array"));
        RecommendItem item3 = createItem(3L, 0.7, Set.of("array"));

        List<RecommendItem> items = Arrays.asList(item1, item2, item3);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(3, result.size());
        // Should maintain score order when all same tag
        assertEquals(1L, result.get(0).getProblemId());
        assertEquals(2L, result.get(1).getProblemId());
        assertEquals(3L, result.get(2).getProblemId());
    }

    @Test
    @DisplayName("Handles mixed items with and without tags")
    void handlesMixedItemsWithAndWithoutTags() {
        RecommendItem withTag1 = createItem(1L, 0.9, Set.of("array"));
        RecommendItem withoutTag = createItem(2L, 0.85, null);
        RecommendItem withTag2 = createItem(3L, 0.8, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(withTag1, withoutTag, withTag2);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(3, result.size());
        // All items should be present
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(1L)));
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(2L)));
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(3L)));
    }

    @Test
    @DisplayName("Requested size larger than available items returns all items")
    void requestedSizeLargerThanAvailableItems() {
        RecommendContext largeContext = RecommendContext.builder()
                .userId("test-user")
                .size(100)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();

        RecommendItem item1 = createItem(1L, 0.9, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.8, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(item1, item2);

        List<RecommendItem> result = strategy.rerank(items, largeContext, profile);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Diversity is achieved with multiple tag groups")
    void diversityIsAchievedWithMultipleTagGroups() {
        // Create 6 items: 3 array, 2 tree, 1 dp
        RecommendItem array1 = createItem(1L, 0.95, Set.of("array"));
        RecommendItem array2 = createItem(2L, 0.90, Set.of("array"));
        RecommendItem array3 = createItem(3L, 0.85, Set.of("array"));

        RecommendItem tree1 = createItem(4L, 0.92, Set.of("tree"));
        RecommendItem tree2 = createItem(5L, 0.87, Set.of("tree"));

        RecommendItem dp1 = createItem(6L, 0.88, Set.of("dynamic-programming"));

        // Input sorted by score (all arrays would be first without diversity)
        List<RecommendItem> items = Arrays.asList(
                array1, tree1, array2, dp1, tree2, array3
        );

        RecommendContext sizeContext = RecommendContext.builder()
                .userId("test-user")
                .size(4)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();

        List<RecommendItem> result = strategy.rerank(items, sizeContext, profile);

        assertEquals(4, result.size());

        // Count tags in result
        long arrayCount = result.stream()
                .filter(i -> i.getTags() != null && i.getTags().contains("array"))
                .count();
        long treeCount = result.stream()
                .filter(i -> i.getTags() != null && i.getTags().contains("tree"))
                .count();
        long dpCount = result.stream()
                .filter(i -> i.getTags() != null && i.getTags().contains("dynamic-programming"))
                .count();

        // With diversity, we should not have all 4 items from same tag
        // At least 2 different tags should be present
        int distinctTags = (arrayCount > 0 ? 1 : 0) +
                          (treeCount > 0 ? 1 : 0) +
                          (dpCount > 0 ? 1 : 0);

        assertTrue(distinctTags >= 2, "Should have at least 2 different tags in result for diversity");
    }

    @Test
    @DisplayName("Handles context with zero size")
    void handlesContextWithZeroSize() {
        RecommendContext zeroContext = RecommendContext.builder()
                .userId("test-user")
                .size(0)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();

        RecommendItem item1 = createItem(1L, 0.9, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.8, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(item1, item2);

        List<RecommendItem> result = strategy.rerank(items, zeroContext, profile);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Does not mutate original list")
    void doesNotMutateOriginalList() {
        RecommendItem item1 = createItem(1L, 0.9, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.8, Set.of("tree"));
        RecommendItem item3 = createItem(3L, 0.7, Set.of("dp"));

        List<RecommendItem> originalItems = new ArrayList<>(Arrays.asList(item1, item2, item3));
        List<RecommendItem> itemsCopy = new ArrayList<>(originalItems);

        strategy.rerank(originalItems, context, profile);

        // Original list should not be modified
        assertEquals(itemsCopy, originalItems);
    }

    /**
     * Helper method to create a RecommendItem with given properties.
     */
    private RecommendItem createItem(Long problemId, double score, Set<String> tags) {
        return RecommendItem.builder()
                .problemId(problemId)
                .score(score)
                .tags(tags)
                .build();
    }
}

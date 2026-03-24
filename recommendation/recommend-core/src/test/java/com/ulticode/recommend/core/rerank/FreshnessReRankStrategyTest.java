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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FreshnessReRankStrategy.
 */
@DisplayName("FreshnessReRankStrategy Tests")
class FreshnessReRankStrategyTest {

    private FreshnessReRankStrategy strategy;
    private RecommendContext context;

    @BeforeEach
    void setUp() {
        strategy = new FreshnessReRankStrategy();
        context = RecommendContext.builder()
                .userId("test-user")
                .size(10)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();
    }

    @Test
    @DisplayName("Strategy has correct priority of 40")
    void strategyHasCorrectPriority() {
        assertEquals(40, strategy.getPriority());
    }

    @Test
    @DisplayName("Strategy name is FreshnessReRankStrategy")
    void strategyNameIsFreshnessReRankStrategy() {
        assertEquals("FreshnessReRankStrategy", strategy.getName());
    }

    @Test
    @DisplayName("Handles empty list by returning empty list")
    void handlesEmptyList() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.8))
                .build();

        List<RecommendItem> result = strategy.rerank(Collections.emptyList(), context, profile);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Handles null list by returning empty list")
    void handlesNullList() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.8))
                .build();

        List<RecommendItem> result = strategy.rerank(null, context, profile);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Handles null profile by returning items unchanged")
    void handlesNullProfile() {
        RecommendItem item1 = createItem(1L, 0.5, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.6, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(item1, item2);

        List<RecommendItem> result = strategy.rerank(items, context, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        // Scores should remain unchanged
        assertEquals(0.5, result.get(0).getScore(), 0.001);
        assertEquals(0.6, result.get(1).getScore(), 0.001);
    }

    @Test
    @DisplayName("Handles profile with null tagMastery by returning items unchanged")
    void handlesProfileWithNullTagMastery() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(null)
                .build();

        RecommendItem item1 = createItem(1L, 0.5, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.6, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(item1, item2);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(2, result.size());
        // Scores should remain unchanged
        assertEquals(0.5, result.get(0).getScore(), 0.001);
        assertEquals(0.6, result.get(1).getScore(), 0.001);
    }

    @Test
    @DisplayName("Handles profile with empty tagMastery by boosting all items")
    void handlesProfileWithEmptyTagMastery() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Collections.emptyMap())
                .build();

        RecommendItem item1 = createItem(1L, 0.5, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.6, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(item1, item2);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(2, result.size());
        // All items should be boosted since no mastery data exists
        assertTrue(result.get(0).getScore() > 0.5, "Item 1 score should be boosted");
        assertTrue(result.get(1).getScore() > 0.6, "Item 2 score should be boosted");
    }

    @Test
    @DisplayName("Items with weak tags get boosted")
    void itemsWithWeakTagsGetBoosted() {
        // User has low mastery in "tree" tag
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("tree", 0.2, "array", 0.9))
                .build();

        // Item with weak tag "tree" should be boosted
        RecommendItem weakTagItem = createItem(1L, 0.5, Set.of("tree"));
        // Item with mastered tag "array" should get minimal boost
        RecommendItem masteredTagItem = createItem(2L, 0.5, Set.of("array"));

        List<RecommendItem> items = Arrays.asList(weakTagItem, masteredTagItem);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(2, result.size());

        // Find items by problem ID
        RecommendItem resultWeakTag = result.stream()
                .filter(i -> i.getProblemId().equals(1L))
                .findFirst().orElseThrow();
        RecommendItem resultMasteredTag = result.stream()
                .filter(i -> i.getProblemId().equals(2L))
                .findFirst().orElseThrow();

        // Item with weak tag should have higher boost
        assertTrue(resultWeakTag.getScore() > resultMasteredTag.getScore(),
                "Item with weak tag should have higher score after boost");
    }

    @Test
    @DisplayName("Items with mastered tags get less or no boost")
    void itemsWithMasteredTagsGetLessOrNoBoost() {
        // User has high mastery in both tags
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.95, "tree", 0.90))
                .build();

        RecommendItem item1 = createItem(1L, 0.5, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.5, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(item1, item2);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(2, result.size());

        // Both items should get minimal boost (since mastery is high)
        // The boost should be proportional to (1 - mastery)
        // For mastery 0.95, boost factor should be minimal
        // For mastery 0.90, boost factor should be slightly higher
        assertTrue(result.get(0).getScore() >= 0.5);
        assertTrue(result.get(1).getScore() >= 0.5);
    }

    @Test
    @DisplayName("Items without tags get no boost")
    void itemsWithoutTagsGetNoBoost() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.5))
                .build();

        RecommendItem itemWithoutTags = createItem(1L, 0.5, null);
        RecommendItem itemWithTags = createItem(2L, 0.5, Set.of("array"));

        List<RecommendItem> items = Arrays.asList(itemWithoutTags, itemWithTags);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(2, result.size());

        // Find item without tags
        RecommendItem resultNoTags = result.stream()
                .filter(i -> i.getProblemId().equals(1L))
                .findFirst().orElseThrow();

        // Item without tags should not be boosted
        assertEquals(0.5, resultNoTags.getScore(), 0.001, "Item without tags should not be boosted");
    }

    @Test
    @DisplayName("Items with empty tags set get no boost")
    void itemsWithEmptyTagsSetGetNoBoost() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.5))
                .build();

        RecommendItem itemWithEmptyTags = createItem(1L, 0.5, Collections.emptySet());
        RecommendItem itemWithTags = createItem(2L, 0.5, Set.of("array"));

        List<RecommendItem> items = Arrays.asList(itemWithEmptyTags, itemWithTags);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(2, result.size());

        // Find item with empty tags
        RecommendItem resultEmptyTags = result.stream()
                .filter(i -> i.getProblemId().equals(1L))
                .findFirst().orElseThrow();

        // Item with empty tags should not be boosted
        assertEquals(0.5, resultEmptyTags.getScore(), 0.001, "Item with empty tags should not be boosted");
    }

    @Test
    @DisplayName("Items with unknown tags get maximum boost")
    void itemsWithUnknownTagsGetMaximumBoost() {
        // User has mastery only for "array"
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.8))
                .build();

        // Item with unknown tag "dynamic-programming" should get maximum boost
        RecommendItem unknownTagItem = createItem(1L, 0.5, Set.of("dynamic-programming"));
        // Item with known tag "array"
        RecommendItem knownTagItem = createItem(2L, 0.5, Set.of("array"));

        List<RecommendItem> items = Arrays.asList(unknownTagItem, knownTagItem);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(2, result.size());

        // Find items by problem ID
        RecommendItem resultUnknownTag = result.stream()
                .filter(i -> i.getProblemId().equals(1L))
                .findFirst().orElseThrow();
        RecommendItem resultKnownTag = result.stream()
                .filter(i -> i.getProblemId().equals(2L))
                .findFirst().orElseThrow();

        // Item with unknown tag should have higher boost (treated as mastery = 0)
        assertTrue(resultUnknownTag.getScore() > resultKnownTag.getScore(),
                "Item with unknown tag should have higher score");
    }

    @Test
    @DisplayName("Multiple tags per item uses minimum mastery for boost")
    void multipleTagsPerItemUsesMinimumMastery() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.9, "tree", 0.3, "dp", 0.5))
                .build();

        // Item with multiple tags - should use minimum mastery (tree = 0.3)
        RecommendItem multiTagItem = createItem(1L, 0.5, Set.of("array", "tree", "dp"));

        List<RecommendItem> items = Collections.singletonList(multiTagItem);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(1, result.size());

        // Boost should be based on minimum mastery (0.3)
        // boostFactor = 1.0 + (1.0 - 0.3) * BOOST_MULTIPLIER
        // Assuming BOOST_MULTIPLIER = 0.5: boostFactor = 1.0 + 0.35 = 1.35
        // New score = 0.5 * 1.35 = 0.675
        assertTrue(result.get(0).getScore() > 0.5,
                "Item should be boosted based on minimum mastery");
    }

    @Test
    @DisplayName("Handles single item correctly")
    void handlesSingleItem() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.5))
                .build();

        RecommendItem item = createItem(1L, 0.5, Set.of("array"));

        List<RecommendItem> items = Collections.singletonList(item);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getProblemId());
    }

    @Test
    @DisplayName("Maintains relative order of items with same boost")
    void maintainsRelativeOrderOfItemsWithSameBoost() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.5))
                .build();

        // Both items have same tag, so same boost factor
        RecommendItem item1 = createItem(1L, 0.8, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.6, Set.of("array"));
        RecommendItem item3 = createItem(3L, 0.4, Set.of("array"));

        List<RecommendItem> items = Arrays.asList(item1, item2, item3);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(3, result.size());
        // Order should be maintained (item1 > item2 > item3)
        assertEquals(1L, result.get(0).getProblemId());
        assertEquals(2L, result.get(1).getProblemId());
        assertEquals(3L, result.get(2).getProblemId());
    }

    @Test
    @DisplayName("Does not mutate original list")
    void doesNotMutateOriginalList() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.5))
                .build();

        RecommendItem item1 = createItem(1L, 0.5, Set.of("array"));
        RecommendItem item2 = createItem(2L, 0.6, Set.of("tree"));

        List<RecommendItem> originalItems = new ArrayList<>(Arrays.asList(item1, item2));
        double originalScore1 = item1.getScore();
        double originalScore2 = item2.getScore();

        strategy.rerank(originalItems, context, profile);

        // Original items' scores should not be modified
        assertEquals(originalScore1, item1.getScore(), 0.001);
        assertEquals(originalScore2, item2.getScore(), 0.001);
    }

    @Test
    @DisplayName("Returns new list with new RecommendItem objects")
    void returnsNewListWithNewRecommendItemObjects() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.5))
                .build();

        RecommendItem item1 = createItem(1L, 0.5, Set.of("array"));

        List<RecommendItem> originalItems = Collections.singletonList(item1);

        List<RecommendItem> result = strategy.rerank(originalItems, context, profile);

        // Should be a different list
        assertNotSame(originalItems, result);
        // Should contain different item objects
        assertNotSame(originalItems.get(0), result.get(0));
    }

    @Test
    @DisplayName("Boost factor is inversely proportional to mastery")
    void boostFactorIsInverselyProportionalToMastery() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of(
                        "low", 0.1,    // Very weak - should get high boost
                        "medium", 0.5, // Medium - should get medium boost
                        "high", 0.9    // Strong - should get low boost
                ))
                .build();

        RecommendItem lowItem = createItem(1L, 0.5, Set.of("low"));
        RecommendItem mediumItem = createItem(2L, 0.5, Set.of("medium"));
        RecommendItem highItem = createItem(3L, 0.5, Set.of("high"));

        List<RecommendItem> items = Arrays.asList(highItem, mediumItem, lowItem);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        // Find items by problem ID
        RecommendItem resultLow = result.stream().filter(i -> i.getProblemId().equals(1L)).findFirst().orElseThrow();
        RecommendItem resultMedium = result.stream().filter(i -> i.getProblemId().equals(2L)).findFirst().orElseThrow();
        RecommendItem resultHigh = result.stream().filter(i -> i.getProblemId().equals(3L)).findFirst().orElseThrow();

        // Verify ordering: low mastery > medium mastery > high mastery
        assertTrue(resultLow.getScore() > resultMedium.getScore(),
                "Low mastery item should have higher score than medium");
        assertTrue(resultMedium.getScore() > resultHigh.getScore(),
                "Medium mastery item should have higher score than high");
    }

    @Test
    @DisplayName("Handles mixed items with and without tags")
    void handlesMixedItemsWithAndWithoutTags() {
        UserProfile profile = UserProfile.builder()
                .userId("test-user")
                .tagMastery(Map.of("array", 0.3))
                .build();

        RecommendItem withTag = createItem(1L, 0.5, Set.of("array"));
        RecommendItem withoutTag = createItem(2L, 0.6, null);
        RecommendItem withUnknownTag = createItem(3L, 0.4, Set.of("tree"));

        List<RecommendItem> items = Arrays.asList(withTag, withoutTag, withUnknownTag);

        List<RecommendItem> result = strategy.rerank(items, context, profile);

        assertEquals(3, result.size());
        // All items should be present
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(1L)));
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(2L)));
        assertTrue(result.stream().anyMatch(i -> i.getProblemId().equals(3L)));
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

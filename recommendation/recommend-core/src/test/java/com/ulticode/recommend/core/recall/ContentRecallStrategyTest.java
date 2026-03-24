package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentRecallStrategy Tests")
class ContentRecallStrategyTest {

    private List<RecommendItem> testProblems;

    @BeforeEach
    void setUp() {
        testProblems = new ArrayList<>();
        // Problems with various tag combinations
        testProblems.add(createProblem(1L, "two-sum", "Two Sum", "Easy",
                Set.of("array", "hash-table")));
        testProblems.add(createProblem(2L, "add-two-numbers", "Add Two Numbers", "Medium",
                Set.of("linked-list", "recursion")));
        testProblems.add(createProblem(3L, "median-of-two-sorted-arrays", "Median of Two Sorted Arrays", "Hard",
                Set.of("array", "binary-search", "divide-and-conquer")));
        testProblems.add(createProblem(4L, "binary-tree-inorder", "Binary Tree Inorder", "Easy",
                Set.of("tree", "hash-table", "stack")));
        testProblems.add(createProblem(5L, "maximum-subarray", "Maximum Subarray", "Medium",
                Set.of("array", "divide-and-conquer", "dp")));
        testProblems.add(createProblem(6L, "merge-sorted-arrays", "Merge Sorted Arrays", "Easy",
                Set.of("array", "two-pointers")));
        testProblems.add(createProblem(7L, "validate-bst", "Validate BST", "Medium",
                Set.of("tree", "dfs")));
        testProblems.add(createProblem(8L, "graph-bfs", "Graph BFS", "Medium",
                Set.of("graph", "bfs")));
        // Problem with no tags
        testProblems.add(createProblem(9L, "no-tags", "No Tags", "Easy",
                Set.of()));
    }

    private RecommendItem createProblem(Long id, String slug, String title, String difficulty,
                                        Set<String> tags) {
        return RecommendItem.builder()
                .problemId(id)
                .slug(slug)
                .title(title)
                .difficulty(difficulty)
                .tags(tags)
                .build();
    }

    @Nested
    @DisplayName("Tag Similarity Calculation Tests")
    class TagSimilarityTests {

        @Test
        @DisplayName("Should calculate Jaccard similarity correctly for overlapping tags")
        void shouldCalculateJaccardSimilarityForOverlappingTags() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            // User has mastered: array (0.8), hash-table (0.6)
            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of(
                            "array", 0.8,
                            "hash-table", 0.6
                    ))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Problem 1 has tags: array, hash-table (both match) -> Jaccard = 2/2 = 1.0
            // Problem 3 has tags: array, binary-search, divide-and-conquer (1 match) -> Jaccard = 1/4 = 0.25
            // Problem 4 has tags: tree, hash-table, stack (1 match) -> Jaccard = 1/4 = 0.25
            // Problem 5 has tags: array, divide-and-conquer, dp (1 match) -> Jaccard = 1/4 = 0.25
            // Problem 6 has tags: array, two-pointers (1 match) -> Jaccard = 1/3 = 0.33

            // Problem 1 (Two Sum) should be first because it has highest similarity
            assertFalse(result.isEmpty());
            assertEquals(1L, result.get(0).getProblemId());
        }

        @Test
        @DisplayName("Should return zero similarity when no tags overlap")
        void shouldReturnZeroSimilarityWhenNoOverlap() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            // User has mastered only graph-related tags
            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of(
                            "graph", 0.8,
                            "bfs", 0.7
                    ))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Problem 8 (Graph BFS) should be first - has graph and bfs tags
            assertFalse(result.isEmpty());
            assertEquals(8L, result.get(0).getProblemId());
        }

        @Test
        @DisplayName("Should handle problem with empty tags")
        void shouldHandleProblemWithEmptyTags() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Problem 9 has no tags - should have similarity 0
            // It should be included but with lowest similarity
            assertTrue(result.stream().anyMatch(item -> item.getProblemId() == 9L));
        }
    }

    @Nested
    @DisplayName("Filter Solved Problems Tests")
    class FilterSolvedProblemsTests {

        @Test
        @DisplayName("Should filter out solved problems")
        void shouldFilterOutSolvedProblems() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of(1L, 3L, 5L, 6L)) // Solved array problems
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should not include solved problems
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 1L));
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 3L));
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 5L));
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 6L));
        }

        @Test
        @DisplayName("Should return all problems when user has not solved any")
        void shouldReturnAllProblemsWhenNoSolved() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertEquals(testProblems.size(), result.size());
        }
    }

    @Nested
    @DisplayName("Sorting Tests")
    class SortingTests {

        @Test
        @DisplayName("Should sort by tag similarity in descending order")
        void shouldSortByTagSimilarityDescending() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of(
                            "array", 0.8,
                            "hash-table", 0.6
                    ))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Verify items are sorted by tagMatchScore in descending order
            for (int i = 0; i < result.size() - 1; i++) {
                assertTrue(result.get(i).getTagMatchScore() >= result.get(i + 1).getTagMatchScore());
            }
        }

        @Test
        @DisplayName("Should assign tagMatchScore to each result item")
        void shouldAssignTagMatchScore() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // All items should have tagMatchScore set
            assertTrue(result.stream().allMatch(item -> item.getTagMatchScore() >= 0));
        }
    }

    @Nested
    @DisplayName("Size Limit Tests")
    class SizeLimitTests {

        @Test
        @DisplayName("Should return correct number of items based on context size")
        void shouldReturnCorrectNumberOfItems() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(3)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Should return all available items if less than requested size")
        void shouldReturnAllAvailableIfLessThanRequested() {
            // Create only 2 problems
            List<RecommendItem> limitedProblems = List.of(
                    createProblem(1L, "p1", "Problem 1", "Easy", Set.of("array")),
                    createProblem(2L, "p2", "Problem 2", "Easy", Set.of("array"))
            );

            ContentRecallStrategy strategy = new ContentRecallStrategy(limitedProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should return empty list when no problems available")
        void shouldReturnEmptyListWhenNoProblems() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(List.of());

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when all problems are solved")
        void shouldReturnEmptyListWhenAllSolved() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            // User has solved all problems
            Set<Long> allSolved = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(allSolved)
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle user with no tag mastery history")
        void shouldHandleUserWithNoTagMastery() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(null)
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should return all problems with similarity 0
            assertNotNull(result);
            assertEquals(testProblems.size(), result.size());
            // All items should have 0 similarity
            assertTrue(result.stream().allMatch(item -> item.getTagMatchScore() == 0.0));
        }

        @Test
        @DisplayName("Should handle user with empty tag mastery")
        void shouldHandleUserWithEmptyTagMastery() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of())
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should return all problems with similarity 0
            assertNotNull(result);
            assertEquals(testProblems.size(), result.size());
            // All items should have 0 similarity
            assertTrue(result.stream().allMatch(item -> item.getTagMatchScore() == 0.0));
        }

        @Test
        @DisplayName("Should handle null solvedProblems in profile")
        void shouldHandleNullSolvedProblems() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(null)
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertEquals(testProblems.size(), result.size());
        }

        @Test
        @DisplayName("Should handle problem with null tags")
        void shouldHandleProblemWithNullTags() {
            List<RecommendItem> problemsWithNullTags = List.of(
                    RecommendItem.builder()
                            .problemId(1L)
                            .slug("null-tags")
                            .title("Null Tags")
                            .tags(null)
                            .build()
            );

            ContentRecallStrategy strategy = new ContentRecallStrategy(problemsWithNullTags);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(0.0, result.get(0).getTagMatchScore());
        }

        @Test
        @DisplayName("Should handle null context")
        void shouldHandleNullContext() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(null, profile);

            // Should use default size (10)
            assertNotNull(result);
            assertTrue(result.size() <= 10);
        }

        @Test
        @DisplayName("Should handle null profile")
        void shouldHandleNullProfile() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, null);

            // Should return all problems with similarity 0
            assertNotNull(result);
            assertEquals(testProblems.size(), result.size());
        }
    }

    @Nested
    @DisplayName("Strategy Metadata Tests")
    class StrategyMetadataTests {

        @Test
        @DisplayName("Should return correct strategy name")
        void shouldReturnCorrectStrategyName() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);
            assertEquals("ContentRecallStrategy", strategy.getName());
        }

        @Test
        @DisplayName("Should return priority 20")
        void shouldReturnPriority20() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(testProblems);
            assertEquals(20, strategy.getPriority());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should apply all filters and sorting correctly")
        void shouldApplyAllFiltersAndSortingCorrectly() {
            // Create specific test data
            List<RecommendItem> problems = List.of(
                    createProblem(1L, "array-hash", "Array Hash", "Easy",
                            Set.of("array", "hash-table")), // 2 matches
                    createProblem(2L, "array-only", "Array Only", "Easy",
                            Set.of("array")), // 1 match
                    createProblem(3L, "no-match", "No Match", "Easy",
                            Set.of("graph")), // 0 matches
                    createProblem(4L, "array-solved", "Array Solved", "Easy",
                            Set.of("array", "hash-table")) // solved
            );

            ContentRecallStrategy strategy = new ContentRecallStrategy(problems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of(
                            "array", 0.8,
                            "hash-table", 0.6
                    ))
                    .solvedProblems(Set.of(4L)) // Problem 4 is solved
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should return 3 problems (problem 4 is filtered as solved)
            assertEquals(3, result.size());

            // Order should be: 1 (2 matches), 2 (1 match), 3 (0 matches)
            assertEquals(1L, result.get(0).getProblemId());
            assertEquals(2L, result.get(1).getProblemId());
            assertEquals(3L, result.get(2).getProblemId());

            // Verify tagMatchScore
            assertTrue(result.get(0).getTagMatchScore() > result.get(1).getTagMatchScore());
            assertTrue(result.get(1).getTagMatchScore() > result.get(2).getTagMatchScore());
        }

        @Test
        @DisplayName("Should handle weighted similarity based on mastery level")
        void shouldHandleWeightedSimilarity() {
            List<RecommendItem> problems = List.of(
                    createProblem(1L, "high-mastery", "High Mastery", "Easy",
                            Set.of("array")), // User has 0.9 mastery
                    createProblem(2L, "low-mastery", "Low Mastery", "Easy",
                            Set.of("graph"))  // User has 0.1 mastery
            );

            ContentRecallStrategy strategy = new ContentRecallStrategy(problems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of(
                            "array", 0.9,
                            "graph", 0.1
                    ))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Problem with high mastery tag should come first
            assertEquals(2, result.size());
            assertEquals(1L, result.get(0).getProblemId());
            assertEquals(2L, result.get(1).getProblemId());

            // High mastery problem should have higher score
            assertTrue(result.get(0).getTagMatchScore() > result.get(1).getTagMatchScore());
        }
    }

    @Nested
    @DisplayName("Null Available Problems Tests")
    class NullAvailableProblemsTests {

        @Test
        @DisplayName("Should handle null available problems list")
        void shouldHandleNullAvailableProblems() {
            ContentRecallStrategy strategy = new ContentRecallStrategy(null);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .tagMastery(Map.of("array", 0.8))
                    .solvedProblems(Set.of())
                    .build();

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}

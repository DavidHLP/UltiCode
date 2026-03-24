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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HotRecallStrategy Tests")
class HotRecallStrategyTest {

    private List<RecommendItem> testProblems;

    @BeforeEach
    void setUp() {
        testProblems = new ArrayList<>();
        // Hot Easy problem
        testProblems.add(createProblem(1L, "two-sum", "Two Sum", "Easy",
                1000, 0.5, Set.of("array", "hash-table")));
        // Hot Medium problem
        testProblems.add(createProblem(2L, "add-two-numbers", "Add Two Numbers", "Medium",
                500, 0.4, Set.of("linked-list")));
        // Hot Hard problem
        testProblems.add(createProblem(3L, "median-of-two-sorted-arrays", "Median of Two Sorted Arrays", "Hard",
                200, 0.35, Set.of("array", "binary-search")));
        // Low submission count (not hot)
        testProblems.add(createProblem(4L, "low-submission", "Low Submission", "Easy",
                50, 0.6, Set.of("array")));
        // Low acceptance rate (not hot)
        testProblems.add(createProblem(5L, "low-acceptance", "Low Acceptance", "Medium",
                500, 0.2, Set.of("dp")));
        // Exactly at threshold
        testProblems.add(createProblem(6L, "threshold-problem", "Threshold Problem", "Easy",
                100, 0.3, Set.of("math")));
        // Very hot problem (highest score)
        testProblems.add(createProblem(7L, "very-hot", "Very Hot", "Medium",
                2000, 0.6, Set.of("array")));
    }

    private RecommendItem createProblem(Long id, String slug, String title, String difficulty,
                                        int submissionCount, double acceptanceRate, Set<String> tags) {
        return RecommendItem.builder()
                .problemId(id)
                .slug(slug)
                .title(title)
                .difficulty(difficulty)
                .tags(tags)
                .qualityScore(submissionCount * acceptanceRate) // Store hot score in qualityScore
                .build();
    }

    @Nested
    @DisplayName("Filter Hot Problems Tests")
    class FilterHotProblemsTests {

        @Test
        @DisplayName("Should filter problems by hot score threshold")
        void shouldFilterByHotScore() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000) // Can get any difficulty
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Hot score = submissionCount * acceptanceRate
            // Problem 4: 50 * 0.6 = 30 (at threshold, should be included)
            // Problem 5: 500 * 0.2 = 100 (above threshold, should be included)
            // Problem 6: 100 * 0.3 = 30 (at threshold, should be included)
            // All problems with qualityScore >= 30 should be included
            assertTrue(result.stream().allMatch(item -> item.getQualityScore() >= 30));
        }

        @Test
        @DisplayName("Should filter problems below hot score threshold")
        void shouldFilterProblemsBelowThreshold() {
            // Create problems where some have low hot scores
            List<RecommendItem> problems = List.of(
                    createProblem(1L, "hot", "Hot Problem", "Easy", 100, 0.5, Set.of("array")), // 50
                    createProblem(2L, "cold", "Cold Problem", "Easy", 50, 0.4, Set.of("array")) // 20 (below 30)
            );

            HotRecallStrategy strategy = new HotRecallStrategy(problems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1000)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should only include problem 1 (hotScore = 50 >= 30)
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getProblemId());
        }

        @Test
        @DisplayName("Should include problems exactly at threshold")
        void shouldIncludeProblemsAtThreshold() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should include problem 6 (submissionCount = 100, acceptanceRate = 0.3, hotScore = 30)
            assertTrue(result.stream().anyMatch(item -> item.getProblemId() == 6L));
        }
    }

    @Nested
    @DisplayName("Filter Solved Problems Tests")
    class FilterSolvedProblemsTests {

        @Test
        @DisplayName("Should filter out solved problems")
        void shouldFilterOutSolvedProblems() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(Set.of(1L, 2L)) // Solved problems 1 and 2
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should not include solved problems
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 1L));
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 2L));
        }

        @Test
        @DisplayName("Should return all problems when user has not solved any")
        void shouldReturnAllProblemsWhenNoSolved() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // All hot problems should be included (not 4, 5 due to thresholds)
            assertTrue(result.size() > 0);
        }
    }

    @Nested
    @DisplayName("Difficulty Matching Tests")
    class DifficultyMatchingTests {

        @Test
        @DisplayName("Should recommend only Easy for rating < 1200")
        void shouldRecommendOnlyEasyForLowRating() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1000) // < 1200
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should only include Easy problems
            assertTrue(result.stream().allMatch(item -> "Easy".equals(item.getDifficulty())));
        }

        @Test
        @DisplayName("Should recommend Easy and Medium for rating 1200-1799")
        void shouldRecommendEasyAndMediumForMidRating() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1500) // 1200 <= rating < 1800
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should only include Easy or Medium problems
            assertTrue(result.stream().allMatch(item ->
                    "Easy".equals(item.getDifficulty()) || "Medium".equals(item.getDifficulty())));
        }

        @Test
        @DisplayName("Should recommend any difficulty for rating >= 1800")
        void shouldRecommendAnyDifficultyForHighRating() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000) // >= 1800
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should include problems of any difficulty
            assertTrue(result.stream().anyMatch(item -> "Easy".equals(item.getDifficulty())));
            assertTrue(result.stream().anyMatch(item -> "Medium".equals(item.getDifficulty())));
            assertTrue(result.stream().anyMatch(item -> "Hard".equals(item.getDifficulty())));
        }

        @Test
        @DisplayName("Should handle boundary rating 1200")
        void shouldHandleBoundaryRating1200() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1200)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // At rating 1200, should get Easy or Medium
            assertTrue(result.stream().allMatch(item ->
                    "Easy".equals(item.getDifficulty()) || "Medium".equals(item.getDifficulty())));
        }

        @Test
        @DisplayName("Should handle boundary rating 1799")
        void shouldHandleBoundaryRating1799() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1799)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // At rating 1799, should get Easy or Medium (not Hard)
            assertTrue(result.stream().allMatch(item ->
                    "Easy".equals(item.getDifficulty()) || "Medium".equals(item.getDifficulty())));
        }
    }

    @Nested
    @DisplayName("Sorting Tests")
    class SortingTests {

        @Test
        @DisplayName("Should sort by hot score (submissionCount * acceptanceRate)")
        void shouldSortByHotScore() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Problem 7 (2000 * 0.6 = 1200) should be first
            // Problem 1 (1000 * 0.5 = 500) should be second
            if (result.size() >= 2) {
                assertTrue(result.get(0).getProblemId() == 7L || result.get(0).getQualityScore() >= result.get(1).getQualityScore());
            }
        }

        @Test
        @DisplayName("Should return items in descending order by score")
        void shouldReturnItemsInDescendingOrder() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Verify descending order
            for (int i = 0; i < result.size() - 1; i++) {
                assertTrue(result.get(i).getQualityScore() >= result.get(i + 1).getQualityScore());
            }
        }
    }

    @Nested
    @DisplayName("Size Limit Tests")
    class SizeLimitTests {

        @Test
        @DisplayName("Should return correct number of items based on context size")
        void shouldReturnCorrectNumberOfItems() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(3)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Should return all available items if less than requested size")
        void shouldReturnAllAvailableIfLessThanRequested() {
            // Create only 2 hot problems
            List<RecommendItem> limitedProblems = List.of(
                    createProblem(1L, "p1", "Problem 1", "Easy", 100, 0.5, Set.of("array")),
                    createProblem(2L, "p2", "Problem 2", "Easy", 100, 0.5, Set.of("array"))
            );

            HotRecallStrategy strategy = new HotRecallStrategy(limitedProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1000) // Easy only
                    .solvedProblems(Set.of())
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
            HotRecallStrategy strategy = new HotRecallStrategy(List.of());

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1500)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when all problems are solved")
        void shouldReturnEmptyListWhenAllSolved() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            // User has solved all problems that pass the hot score threshold
            // Problems with hotScore >= 30: 1(500), 2(200), 3(70), 4(30), 5(100), 6(30), 7(1200)
            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when no problems match difficulty")
        void shouldReturnEmptyListWhenNoMatchingDifficulty() {
            // Create only Hard problems
            List<RecommendItem> hardProblems = List.of(
                    createProblem(1L, "p1", "Problem 1", "Hard", 100, 0.5, Set.of("dp"))
            );

            HotRecallStrategy strategy = new HotRecallStrategy(hardProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1000) // Can only get Easy
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle null solvedProblems in profile")
        void shouldHandleNullSolvedProblems() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(2000)
                    .solvedProblems(null)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should include all hot problems
            assertTrue(result.size() > 0);
        }

        @Test
        @DisplayName("Should handle problem with null difficulty")
        void shouldHandleNullDifficulty() {
            List<RecommendItem> problemsWithNullDifficulty = List.of(
                    RecommendItem.builder()
                            .problemId(1L)
                            .slug("no-difficulty")
                            .title("No Difficulty")
                            .difficulty(null)
                            .qualityScore(100)
                            .build()
            );

            HotRecallStrategy strategy = new HotRecallStrategy(problemsWithNullDifficulty);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1000) // Low rating - normally only Easy
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Null difficulty should be allowed (treated as matching any rating)
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getProblemId());
        }

        @Test
        @DisplayName("Should handle profile with null rating")
        void shouldHandleNullRating() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(0) // Default/unset rating
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // With rating 0, should only get Easy problems
            assertTrue(result.stream().allMatch(item -> "Easy".equals(item.getDifficulty())));
        }
    }

    @Nested
    @DisplayName("Strategy Metadata Tests")
    class StrategyMetadataTests {

        @Test
        @DisplayName("Should return correct strategy name")
        void shouldReturnCorrectStrategyName() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);
            assertEquals("HotRecallStrategy", strategy.getName());
        }

        @Test
        @DisplayName("Should return priority 10")
        void shouldReturnPriority10() {
            HotRecallStrategy strategy = new HotRecallStrategy(testProblems);
            assertEquals(10, strategy.getPriority());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should apply all filters correctly in combination")
        void shouldApplyAllFiltersCorrectly() {
            // Create specific test data
            List<RecommendItem> problems = List.of(
                    // Hot Easy - should be included for low rating
                    createProblem(1L, "hot-easy", "Hot Easy", "Easy", 500, 0.5, Set.of("array")),
                    // Hot Medium - should NOT be included for low rating
                    createProblem(2L, "hot-medium", "Hot Medium", "Medium", 500, 0.5, Set.of("array")),
                    // Not hot (low submission)
                    createProblem(3L, "low-sub", "Low Sub", "Easy", 50, 0.5, Set.of("array")),
                    // Hot but solved
                    createProblem(4L, "hot-solved", "Hot Solved", "Easy", 500, 0.5, Set.of("array"))
            );

            HotRecallStrategy strategy = new HotRecallStrategy(problems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1000) // Low rating - only Easy
                    .solvedProblems(Set.of(4L)) // Solved problem 4
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should only include problem 1
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getProblemId());
        }
    }
}

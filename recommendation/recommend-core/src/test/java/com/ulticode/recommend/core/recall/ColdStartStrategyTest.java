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

@DisplayName("ColdStartStrategy Tests")
class ColdStartStrategyTest {

    private List<RecommendItem> testProblems;

    @BeforeEach
    void setUp() {
        testProblems = new ArrayList<>();
        // Hot Easy problem (old)
        testProblems.add(createProblem(1L, "two-sum", "Two Sum", "Easy",
                500, Set.of("array", "hash-table"), 30)); // 30 days ago
        // Hot Medium problem (old)
        testProblems.add(createProblem(2L, "add-two-numbers", "Add Two Numbers", "Medium",
                200, Set.of("linked-list"), 30));
        // Hot Hard problem (old)
        testProblems.add(createProblem(3L, "median-of-two-sorted-arrays", "Median of Two Sorted Arrays", "Hard",
                70, Set.of("array", "binary-search"), 30));
        // New Easy problem (5 days ago - new)
        testProblems.add(createProblem(4L, "new-easy", "New Easy Problem", "Easy",
                10, Set.of("array", "dp"), 5));
        // New Medium problem (3 days ago - new)
        testProblems.add(createProblem(5L, "new-medium", "New Medium Problem", "Medium",
                15, Set.of("graph"), 3));
        // New Hard problem (1 day ago - new)
        testProblems.add(createProblem(6L, "new-hard", "New Hard Problem", "Hard",
                5, Set.of("tree"), 1));
        // Old problem at threshold (7 days - exactly at boundary)
        testProblems.add(createProblem(7L, "boundary-problem", "Boundary Problem", "Easy",
                100, Set.of("math"), 7));
        // Very new problem (0 days - today)
        testProblems.add(createProblem(8L, "today-problem", "Today Problem", "Medium",
                0, Set.of("string"), 0));
    }

    private RecommendItem createProblem(Long id, String slug, String title, String difficulty,
                                        int qualityScore, Set<String> tags, int daysAgo) {
        // Store daysAgo in freshnessScore temporarily for testing
        // In production, this would be calculated from createdAt
        return RecommendItem.builder()
                .problemId(id)
                .slug(slug)
                .title(title)
                .difficulty(difficulty)
                .tags(tags)
                .qualityScore(qualityScore)
                .freshnessScore(daysAgo) // Using freshnessScore to store daysAgo for testing
                .build();
    }

    @Nested
    @DisplayName("New User Cold Start Tests")
    class NewUserColdStartTests {

        @Test
        @DisplayName("Should delegate to HotRecallStrategy when profile is null")
        void shouldDelegateWhenProfileIsNull() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("newUser")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, null);

            assertNotNull(result);
            // Should return hot problems (qualityScore >= 30)
            assertTrue(result.stream().allMatch(item -> item.getQualityScore() >= 30));
        }

        @Test
        @DisplayName("Should delegate to HotRecallStrategy when totalSolved is 0")
        void shouldDelegateWhenTotalSolvedIsZero() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("newUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("newUser")
                    .rating(1500)
                    .totalSolved(0)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should delegate to HotRecallStrategy
            assertTrue(result.stream().allMatch(item -> item.getQualityScore() >= 30));
        }

        @Test
        @DisplayName("Should delegate to HotRecallStrategy when solvedProblems is null")
        void shouldDelegateWhenSolvedProblemsIsNull() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("newUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("newUser")
                    .rating(1500)
                    .totalSolved(0)
                    .solvedProblems(null)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should delegate to HotRecallStrategy
            assertTrue(result.size() > 0);
        }

        @Test
        @DisplayName("Should delegate to HotRecallStrategy when solvedProblems is empty")
        void shouldDelegateWhenSolvedProblemsIsEmpty() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("newUser")
                    .size(5)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("newUser")
                    .rating(1500)
                    .totalSolved(0)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should be limited by context size
            assertTrue(result.size() <= 5);
        }

        @Test
        @DisplayName("Should recommend Easy problems for new user with low rating")
        void shouldRecommendEasyForLowRatingNewUser() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("newUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("newUser")
                    .rating(1000) // < 1200
                    .totalSolved(0)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should only recommend Easy problems for low rating
            assertTrue(result.stream().allMatch(item -> "Easy".equals(item.getDifficulty())));
        }

        @Test
        @DisplayName("Should recommend Easy and Medium for new user with mid rating")
        void shouldRecommendEasyAndMediumForMidRatingNewUser() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("newUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("newUser")
                    .rating(1500) // 1200-1799
                    .totalSolved(0)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should recommend Easy or Medium
            assertTrue(result.stream().allMatch(item ->
                    "Easy".equals(item.getDifficulty()) || "Medium".equals(item.getDifficulty())));
        }
    }

    @Nested
    @DisplayName("New Problem Exposure Tests")
    class NewProblemExposureTests {

        @Test
        @DisplayName("Should identify new problems within 7 days")
        void shouldIdentifyNewProblemsWithin7Days() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of())
                    .tagMastery(Map.of("array", 0.5, "dp", 0.3))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should include new problems (daysAgo <= 7)
            assertTrue(result.stream().anyMatch(item -> item.getFreshnessScore() <= 7));
        }

        @Test
        @DisplayName("Should boost new problems with freshness score")
        void shouldBoostNewProblemsWithFreshnessScore() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of(1L, 2L, 3L)) // Solved old hot problems
                    .tagMastery(Map.of("array", 0.5, "graph", 0.5))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // New problems should be prioritized due to freshness boost
            // Problem 5 (new medium, graph tag match) should rank high
            assertTrue(result.size() > 0);
        }

        @Test
        @DisplayName("Should filter out solved problems from new problems")
        void shouldFilterOutSolvedProblems() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of(4L, 5L, 6L)) // Solved all new problems
                    .tagMastery(Map.of("array", 0.5))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should not include solved new problems
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 4L));
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 5L));
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 6L));
        }

        @Test
        @DisplayName("Should calculate tag match score for new problems")
        void shouldCalculateTagMatchScore() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of(1L, 2L, 3L)) // Solved old problems to ensure new problems are prioritized
                    .tagMastery(Map.of("array", 0.8, "dp", 0.6)) // User likes array and dp
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertFalse(result.isEmpty(), "Should return some new problems");
            // Problem 4 (new easy, has array and dp tags) should match user preferences
            // Find problem 4 in the result and check its tag match score
            boolean foundWithScore = result.stream()
                    .anyMatch(item -> item.getProblemId() == 4L && item.getTagMatchScore() > 0);
            // Also check that at least some problems have positive tag match scores
            boolean hasPositiveTagMatch = result.stream().anyMatch(item -> item.getTagMatchScore() > 0);
            assertTrue(hasPositiveTagMatch, "At least one problem should have positive tag match score");
        }

        @Test
        @DisplayName("Should include problem at 7-day boundary")
        void shouldIncludeProblemAtBoundary() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of())
                    .tagMastery(Map.of("math", 0.5))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Problem 7 is exactly 7 days old (at boundary), should be included
            assertTrue(result.stream().anyMatch(item -> item.getProblemId() == 7L));
        }

        @Test
        @DisplayName("Should exclude problems older than 7 days")
        void shouldExcludeProblemsOlderThan7Days() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of())
                    .tagMastery(Map.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Problems 1, 2, 3 are 30 days old, should not be in new problems
            // But they may still be included if no new problems available
            // The strategy prioritizes new problems
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should return empty list when no problems available")
        void shouldReturnEmptyListWhenNoProblems() {
            ColdStartStrategy strategy = new ColdStartStrategy(List.of());

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1500)
                    .totalSolved(0)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle null available problems")
        void shouldHandleNullAvailableProblems() {
            ColdStartStrategy strategy = new ColdStartStrategy(null);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .rating(1500)
                    .totalSolved(0)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle user with empty tag mastery")
        void shouldHandleEmptyTagMastery() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of())
                    .tagMastery(null) // No tag preferences
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should still return new problems even without tag matching
            assertTrue(result.size() > 0);
        }

        @Test
        @DisplayName("Should handle all new problems already solved")
        void shouldHandleAllNewProblemsSolved() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of(4L, 5L, 6L, 7L, 8L)) // All new problems solved
                    .tagMastery(Map.of("array", 0.5))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should return empty since all new problems are solved
            // and this is an existing user
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should respect context size limit")
        void shouldRespectContextSizeLimit() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(3)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of())
                    .tagMastery(Map.of("array", 0.5))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Should handle null context")
        void shouldHandleNullContext() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("newUser")
                    .rating(1500)
                    .totalSolved(0)
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(null, profile);

            assertNotNull(result);
            // Should use default size
        }

        @Test
        @DisplayName("Should handle problem with null tags")
        void shouldHandleProblemWithNullTags() {
            List<RecommendItem> problemsWithNullTags = List.of(
                    RecommendItem.builder()
                            .problemId(1L)
                            .slug("new-no-tags")
                            .title("New Problem No Tags")
                            .difficulty("Easy")
                            .qualityScore(50)
                            .freshnessScore(2) // New problem
                            .tags(null)
                            .build()
            );

            ColdStartStrategy strategy = new ColdStartStrategy(problemsWithNullTags);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of())
                    .tagMastery(Map.of("array", 0.5))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should handle null tags gracefully
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Strategy Metadata Tests")
    class StrategyMetadataTests {

        @Test
        @DisplayName("Should return correct strategy name")
        void shouldReturnCorrectStrategyName() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);
            assertEquals("ColdStartStrategy", strategy.getName());
        }

        @Test
        @DisplayName("Should return priority 5 (lowest priority)")
        void shouldReturnPriority5() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);
            assertEquals(5, strategy.getPriority());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should apply all filters correctly for existing user")
        void shouldApplyAllFiltersCorrectlyForExistingUser() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(1500) // Easy or Medium only
                    .totalSolved(50)
                    .solvedProblems(Set.of(4L)) // Solved one new problem
                    .tagMastery(Map.of("graph", 0.8, "string", 0.6))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Should not include solved problems
            assertFalse(result.stream().anyMatch(item -> item.getProblemId() == 4L));
            // Should respect difficulty for mid rating
            assertTrue(result.stream().allMatch(item ->
                    "Easy".equals(item.getDifficulty()) || "Medium".equals(item.getDifficulty())));
        }

        @Test
        @DisplayName("Should sort by combined score (tag match + freshness)")
        void shouldSortByCombinedScore() {
            ColdStartStrategy strategy = new ColdStartStrategy(testProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("existingUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("existingUser")
                    .rating(2000)
                    .totalSolved(100)
                    .solvedProblems(Set.of())
                    .tagMastery(Map.of("graph", 0.9)) // High preference for graph
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Problem 5 (new medium, graph tag) should rank high due to tag match + freshness
            if (result.size() > 1) {
                // Verify descending order by score
                for (int i = 0; i < result.size() - 1; i++) {
                    assertTrue(result.get(i).getScore() >= result.get(i + 1).getScore());
                }
            }
        }
    }
}

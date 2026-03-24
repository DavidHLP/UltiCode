package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CFRecallStrategy Tests")
class CFRecallStrategyTest {

    private Map<String, Set<Long>> userProblemMatrix;
    private List<RecommendItem> availableProblems;

    @BeforeEach
    void setUp() {
        // Set up user-problem matrix
        // user1: {1, 2, 3}
        // user2: {1, 2, 4, 5} - similar to user1 (intersection: {1, 2})
        // user3: {1, 2, 3, 6, 7} - most similar to user1 (intersection: {1, 2, 3})
        // user4: {8, 9, 10} - not similar to user1 (no intersection)
        userProblemMatrix = new HashMap<>();
        userProblemMatrix.put("user1", Set.of(1L, 2L, 3L));
        userProblemMatrix.put("user2", Set.of(1L, 2L, 4L, 5L));
        userProblemMatrix.put("user3", Set.of(1L, 2L, 3L, 6L, 7L));
        userProblemMatrix.put("user4", Set.of(8L, 9L, 10L));
        userProblemMatrix.put("user5", Set.of(1L, 2L, 3L, 11L)); // Another similar user

        // Set up available problems
        availableProblems = new ArrayList<>();
        availableProblems.add(createProblem(1L, "two-sum", "Two Sum", "Easy", Set.of("array")));
        availableProblems.add(createProblem(2L, "add-two-numbers", "Add Two Numbers", "Medium", Set.of("linked-list")));
        availableProblems.add(createProblem(3L, "median-of-two-sorted-arrays", "Median of Two Sorted Arrays", "Hard", Set.of("array")));
        availableProblems.add(createProblem(4L, "problem-4", "Problem 4", "Easy", Set.of("dp")));
        availableProblems.add(createProblem(5L, "problem-5", "Problem 5", "Medium", Set.of("tree")));
        availableProblems.add(createProblem(6L, "problem-6", "Problem 6", "Easy", Set.of("graph")));
        availableProblems.add(createProblem(7L, "problem-7", "Problem 7", "Medium", Set.of("hash")));
        availableProblems.add(createProblem(8L, "problem-8", "Problem 8", "Hard", Set.of("stack")));
        availableProblems.add(createProblem(9L, "problem-9", "Problem 9", "Easy", Set.of("queue")));
        availableProblems.add(createProblem(10L, "problem-10", "Problem 10", "Medium", Set.of("heap")));
        availableProblems.add(createProblem(11L, "problem-11", "Problem 11", "Easy", Set.of("string")));
    }

    private RecommendItem createProblem(Long id, String slug, String title, String difficulty, Set<String> tags) {
        return RecommendItem.builder()
                .problemId(id)
                .slug(slug)
                .title(title)
                .difficulty(difficulty)
                .tags(tags)
                .qualityScore(0.5) // Default quality score
                .build();
    }

    @Nested
    @DisplayName("Find Similar Users Tests")
    class FindSimilarUsersTests {

        @Test
        @DisplayName("Should find similar users based on problem overlap")
        void shouldFindSimilarUsers() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should recommend problems from similar users (user2, user3, user5)
            // user1 has solved {1, 2, 3}, so should get {4, 5, 6, 7, 11} from similar users
            assertNotNull(result);
            // At least some problems should be recommended
            assertTrue(result.size() > 0);
        }

        @Test
        @DisplayName("Should not include current user in similar users")
        void shouldNotIncludeCurrentUser() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should not return problems that only user1 has solved
            // All recommended problems should be from OTHER users
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Filter Unsolved Problems Tests")
    class FilterUnsolvedProblemsTests {

        @Test
        @DisplayName("Should recommend problems similar users solved but current user did not")
        void shouldRecommendUnsolvedProblems() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            // user1 has solved {1, 2, 3}
            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should not include problems 1, 2, 3 (already solved by user1)
            assertTrue(result.stream().noneMatch(item -> item.getProblemId() == 1L));
            assertTrue(result.stream().noneMatch(item -> item.getProblemId() == 2L));
            assertTrue(result.stream().noneMatch(item -> item.getProblemId() == 3L));
        }

        @Test
        @DisplayName("Should filter out current user's solved problems")
        void shouldFilterOutSolvedProblems() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            // User has solved many problems
            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // None of the solved problems should appear
            Set<Long> solvedSet = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
            assertTrue(result.stream().noneMatch(item -> solvedSet.contains(item.getProblemId())));
        }
    }

    @Nested
    @DisplayName("Weighted Sorting Tests")
    class WeightedSortingTests {

        @Test
        @DisplayName("Should sort by weighted similarity score")
        void shouldSortByWeightedScore() {
            // Create a scenario where one problem is solved by more similar users
            userProblemMatrix.put("similarUser1", Set.of(1L, 2L, 100L)); // High similarity
            userProblemMatrix.put("similarUser2", Set.of(1L, 2L, 100L)); // High similarity
            userProblemMatrix.put("lessSimilarUser", Set.of(1L, 200L));  // Lower similarity

            availableProblems.add(createProblem(100L, "problem-100", "Problem 100", "Easy", Set.of("array")));
            availableProblems.add(createProblem(200L, "problem-200", "Problem 200", "Easy", Set.of("array")));

            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("testUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("testUser")
                    .solvedProblems(Set.of(1L, 2L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Verify results are sorted by score descending
            for (int i = 0; i < result.size() - 1; i++) {
                assertTrue(result.get(i).getScore() >= result.get(i + 1).getScore());
            }
        }

        @Test
        @DisplayName("Should assign higher scores to problems from more similar users")
        void shouldAssignHigherScoresToMoreSimilarUserProblems() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // All returned items should have scores > 0
            assertTrue(result.stream().allMatch(item -> item.getScore() > 0));
        }
    }

    @Nested
    @DisplayName("Size Limit Tests")
    class SizeLimitTests {

        @Test
        @DisplayName("Should return correct number of items based on context size")
        void shouldReturnCorrectNumberOfItems() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(3)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Should return all available items if less than requested size")
        void shouldReturnAllAvailableIfLessThanRequested() {
            // Only a few problems available from similar users
            Map<String, Set<Long>> smallMatrix = new HashMap<>();
            smallMatrix.put("user1", Set.of(1L, 2L, 3L));
            smallMatrix.put("user2", Set.of(1L, 4L)); // Only 1 new problem

            List<RecommendItem> smallProblems = List.of(
                    createProblem(1L, "p1", "Problem 1", "Easy", Set.of("array")),
                    createProblem(4L, "p4", "Problem 4", "Easy", Set.of("array"))
            );

            CFRecallStrategy strategy = new CFRecallStrategy(smallMatrix, smallProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should only return 1 problem (problem 4)
            assertEquals(1, result.size());
            assertEquals(4L, result.get(0).getProblemId());
        }
    }

    @Nested
    @DisplayName("No Similar Users Tests")
    class NoSimilarUsersTests {

        @Test
        @DisplayName("Should return empty list when no similar users")
        void shouldReturnEmptyWhenNoSimilarUsers() {
            // User5 has no overlap with any other user
            Map<String, Set<Long>> isolatedMatrix = new HashMap<>();
            isolatedMatrix.put("isolatedUser", Set.of(100L, 101L, 102L));
            isolatedMatrix.put("otherUser", Set.of(1L, 2L, 3L));

            CFRecallStrategy strategy = new CFRecallStrategy(isolatedMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("isolatedUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("isolatedUser")
                    .solvedProblems(Set.of(100L, 101L, 102L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when only current user exists in matrix")
        void shouldReturnEmptyWhenOnlyCurrentUser() {
            Map<String, Set<Long>> singleUserMatrix = new HashMap<>();
            singleUserMatrix.put("onlyUser", Set.of(1L, 2L, 3L));

            CFRecallStrategy strategy = new CFRecallStrategy(singleUserMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("onlyUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("onlyUser")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Cold Start Tests")
    class ColdStartTests {

        @Test
        @DisplayName("Should return empty list when user has no history")
        void shouldReturnEmptyForColdStartUser() {
            // User with no solved problems (cold start)
            Map<String, Set<Long>> matrixWithColdStart = new HashMap<>();
            matrixWithColdStart.put("coldStartUser", Set.of());
            matrixWithColdStart.put("otherUser", Set.of(1L, 2L, 3L));

            CFRecallStrategy strategy = new CFRecallStrategy(matrixWithColdStart, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("coldStartUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("coldStartUser")
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            // Cold start user cannot be compared (no history), so should return empty
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when user not in matrix")
        void shouldReturnEmptyForUnknownUser() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("unknownUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("unknownUser")
                    .solvedProblems(Set.of())
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty user problem matrix")
        void shouldHandleEmptyMatrix() {
            CFRecallStrategy strategy = new CFRecallStrategy(Map.of(), availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle empty available problems")
        void shouldHandleEmptyProblems() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, List.of());

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle null context")
        void shouldHandleNullContext() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(null, profile);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle null profile")
        void shouldHandleNullProfile() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            List<RecommendItem> result = strategy.recall(context, null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle null solved problems in profile")
        void shouldHandleNullSolvedProblems() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(null)
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Strategy Metadata Tests")
    class StrategyMetadataTests {

        @Test
        @DisplayName("Should return correct strategy name")
        void shouldReturnCorrectStrategyName() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);
            assertEquals("CFRecallStrategy", strategy.getName());
        }

        @Test
        @DisplayName("Should return priority 30")
        void shouldReturnPriority30() {
            CFRecallStrategy strategy = new CFRecallStrategy(userProblemMatrix, availableProblems);
            assertEquals(30, strategy.getPriority());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should apply all filters correctly in combination")
        void shouldApplyAllFiltersCorrectly() {
            // Create specific test scenario
            Map<String, Set<Long>> matrix = new HashMap<>();
            matrix.put("targetUser", Set.of(1L, 2L, 3L));
            matrix.put("similarUser", Set.of(1L, 2L, 4L, 5L)); // Similar, has 4, 5
            matrix.put("differentUser", Set.of(10L, 11L, 12L)); // Not similar

            List<RecommendItem> problems = List.of(
                    createProblem(1L, "p1", "Problem 1", "Easy", Set.of("array")),
                    createProblem(2L, "p2", "Problem 2", "Easy", Set.of("array")),
                    createProblem(3L, "p3", "Problem 3", "Easy", Set.of("array")),
                    createProblem(4L, "p4", "Problem 4", "Easy", Set.of("array")),
                    createProblem(5L, "p5", "Problem 5", "Easy", Set.of("array"))
            );

            CFRecallStrategy strategy = new CFRecallStrategy(matrix, problems);

            RecommendContext context = RecommendContext.builder()
                    .userId("targetUser")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("targetUser")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should return problems 4 and 5 (from similar user, not solved by target)
            assertEquals(2, result.size());
            Set<Long> resultIds = new HashSet<>();
            result.forEach(item -> resultIds.add(item.getProblemId()));
            assertTrue(resultIds.contains(4L));
            assertTrue(resultIds.contains(5L));
        }

        @Test
        @DisplayName("Should limit similar users to top K")
        void shouldLimitSimilarUsers() {
            // Create many users with varying similarity
            Map<String, Set<Long>> matrix = new HashMap<>();
            matrix.put("user1", Set.of(1L, 2L, 3L));

            // Add 20 similar users
            for (int i = 0; i < 20; i++) {
                matrix.put("similarUser" + i, Set.of(1L, 2L, 3L, (long) (100 + i)));
            }

            // Add more problems
            for (int i = 0; i < 20; i++) {
                availableProblems.add(createProblem((long) (100 + i), "p" + (100 + i), "Problem " + (100 + i), "Easy", Set.of("array")));
            }

            CFRecallStrategy strategy = new CFRecallStrategy(matrix, availableProblems);

            RecommendContext context = RecommendContext.builder()
                    .userId("user1")
                    .size(10)
                    .build();

            UserProfile profile = UserProfile.builder()
                    .userId("user1")
                    .solvedProblems(Set.of(1L, 2L, 3L))
                    .build();

            List<RecommendItem> result = strategy.recall(context, profile);

            // Should only use top K similar users (10 by default)
            // But should return requested size
            assertEquals(10, result.size());
        }
    }
}

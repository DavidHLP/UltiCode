package com.ulticode.recommend.feature;

import com.ulticode.recommend.feature.model.ProblemFeatures;
import com.ulticode.recommend.feature.model.UserFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FeatureStore Tests")
class FeatureStoreTest {

    private FeatureStore featureStore;

    @BeforeEach
    void setUp() {
        featureStore = new FeatureStore();
    }

    // ==================== User Features Storage Tests ====================

    @Nested
    @DisplayName("putUserFeatures and getUserFeatures Tests")
    class UserFeaturesStorageTests {

        @Test
        @DisplayName("Should store and retrieve user features")
        void shouldStoreAndRetrieveUserFeatures() {
            // Arrange
            String userId = "user123";
            UserFeatures features = createUserFeatures(userId);

            // Act
            featureStore.putUserFeatures(userId, features);
            Optional<UserFeatures> retrieved = featureStore.getUserFeatures(userId);

            // Assert
            assertTrue(retrieved.isPresent());
            assertEquals(userId, retrieved.get().getUserId());
            assertEquals(features.getActivityLevel(), retrieved.get().getActivityLevel(), 0.001);
        }

        @Test
        @DisplayName("Should return empty optional for non-existent user")
        void shouldReturnEmptyForNonExistentUser() {
            // Act
            Optional<UserFeatures> retrieved = featureStore.getUserFeatures("nonexistent");

            // Assert
            assertFalse(retrieved.isPresent());
        }

        @Test
        @DisplayName("Should overwrite existing user features")
        void shouldOverwriteExistingUserFeatures() {
            // Arrange
            String userId = "user123";
            UserFeatures original = createUserFeatures(userId, 0.5);
            UserFeatures updated = createUserFeatures(userId, 0.8);

            // Act
            featureStore.putUserFeatures(userId, original);
            featureStore.putUserFeatures(userId, updated);
            Optional<UserFeatures> retrieved = featureStore.getUserFeatures(userId);

            // Assert
            assertTrue(retrieved.isPresent());
            assertEquals(0.8, retrieved.get().getActivityLevel(), 0.001);
        }

        @Test
        @DisplayName("Should throw exception for null userId in putUserFeatures")
        void shouldThrowExceptionForNullUserIdInPut() {
            // Arrange
            UserFeatures features = createUserFeatures("user1");

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.putUserFeatures(null, features));
        }

        @Test
        @DisplayName("Should throw exception for null userId in getUserFeatures")
        void shouldThrowExceptionForNullUserIdInGet() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.getUserFeatures(null));
        }

        @Test
        @DisplayName("Should throw exception for null features in putUserFeatures")
        void shouldThrowExceptionForNullFeatures() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.putUserFeatures("user1", null));
        }
    }

    // ==================== Problem Features Storage Tests ====================

    @Nested
    @DisplayName("putProblemFeatures and getProblemFeatures Tests")
    class ProblemFeaturesStorageTests {

        @Test
        @DisplayName("Should store and retrieve problem features")
        void shouldStoreAndRetrieveProblemFeatures() {
            // Arrange
            Long problemId = 1L;
            ProblemFeatures features = createProblemFeatures(problemId);

            // Act
            featureStore.putProblemFeatures(problemId, features);
            Optional<ProblemFeatures> retrieved = featureStore.getProblemFeatures(problemId);

            // Assert
            assertTrue(retrieved.isPresent());
            assertEquals(problemId, retrieved.get().getProblemId());
            assertEquals(features.getDifficultyScore(), retrieved.get().getDifficultyScore(), 0.001);
        }

        @Test
        @DisplayName("Should return empty optional for non-existent problem")
        void shouldReturnEmptyForNonExistentProblem() {
            // Act
            Optional<ProblemFeatures> retrieved = featureStore.getProblemFeatures(999L);

            // Assert
            assertFalse(retrieved.isPresent());
        }

        @Test
        @DisplayName("Should overwrite existing problem features")
        void shouldOverwriteExistingProblemFeatures() {
            // Arrange
            Long problemId = 1L;
            ProblemFeatures original = createProblemFeatures(problemId, 0.3);
            ProblemFeatures updated = createProblemFeatures(problemId, 0.7);

            // Act
            featureStore.putProblemFeatures(problemId, original);
            featureStore.putProblemFeatures(problemId, updated);
            Optional<ProblemFeatures> retrieved = featureStore.getProblemFeatures(problemId);

            // Assert
            assertTrue(retrieved.isPresent());
            assertEquals(0.7, retrieved.get().getDifficultyScore(), 0.001);
        }

        @Test
        @DisplayName("Should throw exception for null problemId in putProblemFeatures")
        void shouldThrowExceptionForNullProblemIdInPut() {
            // Arrange
            ProblemFeatures features = createProblemFeatures(1L);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.putProblemFeatures(null, features));
        }

        @Test
        @DisplayName("Should throw exception for null problemId in getProblemFeatures")
        void shouldThrowExceptionForNullProblemIdInGet() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.getProblemFeatures(null));
        }

        @Test
        @DisplayName("Should throw exception for null features in putProblemFeatures")
        void shouldThrowExceptionForNullFeatures() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.putProblemFeatures(1L, null));
        }
    }

    // ==================== Batch Operations Tests ====================

    @Nested
    @DisplayName("Batch Operations Tests")
    class BatchOperationsTests {

        @Test
        @DisplayName("Should store multiple user features at once")
        void shouldStoreMultipleUserFeatures() {
            // Arrange
            Map<String, UserFeatures> featuresMap = new HashMap<>();
            featuresMap.put("user1", createUserFeatures("user1"));
            featuresMap.put("user2", createUserFeatures("user2"));
            featuresMap.put("user3", createUserFeatures("user3"));

            // Act
            featureStore.putAllUserFeatures(featuresMap);

            // Assert
            assertTrue(featureStore.getUserFeatures("user1").isPresent());
            assertTrue(featureStore.getUserFeatures("user2").isPresent());
            assertTrue(featureStore.getUserFeatures("user3").isPresent());
            assertEquals(3, featureStore.getUserCount());
        }

        @Test
        @DisplayName("Should retrieve multiple user features at once")
        void shouldRetrieveMultipleUserFeatures() {
            // Arrange
            featureStore.putUserFeatures("user1", createUserFeatures("user1"));
            featureStore.putUserFeatures("user2", createUserFeatures("user2"));
            featureStore.putUserFeatures("user3", createUserFeatures("user3"));

            Set<String> userIds = Set.of("user1", "user2", "user3", "nonexistent");

            // Act
            Map<String, UserFeatures> retrieved = featureStore.getAllUserFeatures(userIds);

            // Assert
            assertEquals(3, retrieved.size()); // nonexistent not included
            assertTrue(retrieved.containsKey("user1"));
            assertTrue(retrieved.containsKey("user2"));
            assertTrue(retrieved.containsKey("user3"));
            assertFalse(retrieved.containsKey("nonexistent"));
        }

        @Test
        @DisplayName("Should store multiple problem features at once")
        void shouldStoreMultipleProblemFeatures() {
            // Arrange
            Map<Long, ProblemFeatures> featuresMap = new HashMap<>();
            featuresMap.put(1L, createProblemFeatures(1L));
            featuresMap.put(2L, createProblemFeatures(2L));
            featuresMap.put(3L, createProblemFeatures(3L));

            // Act
            featureStore.putAllProblemFeatures(featuresMap);

            // Assert
            assertTrue(featureStore.getProblemFeatures(1L).isPresent());
            assertTrue(featureStore.getProblemFeatures(2L).isPresent());
            assertTrue(featureStore.getProblemFeatures(3L).isPresent());
            assertEquals(3, featureStore.getProblemCount());
        }

        @Test
        @DisplayName("Should retrieve multiple problem features at once")
        void shouldRetrieveMultipleProblemFeatures() {
            // Arrange
            featureStore.putProblemFeatures(1L, createProblemFeatures(1L));
            featureStore.putProblemFeatures(2L, createProblemFeatures(2L));
            featureStore.putProblemFeatures(3L, createProblemFeatures(3L));

            Set<Long> problemIds = Set.of(1L, 2L, 3L, 999L);

            // Act
            Map<Long, ProblemFeatures> retrieved = featureStore.getAllProblemFeatures(problemIds);

            // Assert
            assertEquals(3, retrieved.size()); // 999L not included
            assertTrue(retrieved.containsKey(1L));
            assertTrue(retrieved.containsKey(2L));
            assertTrue(retrieved.containsKey(3L));
            assertFalse(retrieved.containsKey(999L));
        }

        @Test
        @DisplayName("Should throw exception for null map in putAllUserFeatures")
        void shouldThrowExceptionForNullMapInPutAllUserFeatures() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.putAllUserFeatures(null));
        }

        @Test
        @DisplayName("Should throw exception for null set in getAllUserFeatures")
        void shouldThrowExceptionForNullSetInGetAllUserFeatures() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.getAllUserFeatures(null));
        }

        @Test
        @DisplayName("Should handle empty map in putAllUserFeatures")
        void shouldHandleEmptyMapInPutAllUserFeatures() {
            // Act
            featureStore.putAllUserFeatures(new HashMap<>());

            // Assert
            assertEquals(0, featureStore.getUserCount());
        }

        @Test
        @DisplayName("Should handle empty set in getAllUserFeatures")
        void shouldHandleEmptySetInGetAllUserFeatures() {
            // Arrange
            featureStore.putUserFeatures("user1", createUserFeatures("user1"));

            // Act
            Map<String, UserFeatures> result = featureStore.getAllUserFeatures(Collections.emptySet());

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    // ==================== Cache Management Tests ====================

    @Nested
    @DisplayName("Cache Management Tests")
    class CacheManagementTests {

        @Test
        @DisplayName("Should invalidate user from cache")
        void shouldInvalidateUserFromCache() {
            // Arrange
            String userId = "user123";
            featureStore.putUserFeatures(userId, createUserFeatures(userId));

            // Act
            featureStore.invalidateUser(userId);

            // Assert
            assertFalse(featureStore.getUserFeatures(userId).isPresent());
        }

        @Test
        @DisplayName("Should invalidate problem from cache")
        void shouldInvalidateProblemFromCache() {
            // Arrange
            Long problemId = 1L;
            featureStore.putProblemFeatures(problemId, createProblemFeatures(problemId));

            // Act
            featureStore.invalidateProblem(problemId);

            // Assert
            assertFalse(featureStore.getProblemFeatures(problemId).isPresent());
        }

        @Test
        @DisplayName("Should clear all cached data")
        void shouldClearAllCachedData() {
            // Arrange
            featureStore.putUserFeatures("user1", createUserFeatures("user1"));
            featureStore.putUserFeatures("user2", createUserFeatures("user2"));
            featureStore.putProblemFeatures(1L, createProblemFeatures(1L));
            featureStore.putProblemFeatures(2L, createProblemFeatures(2L));

            // Act
            featureStore.clear();

            // Assert
            assertEquals(0, featureStore.size());
            assertEquals(0, featureStore.getUserCount());
            assertEquals(0, featureStore.getProblemCount());
        }

        @Test
        @DisplayName("Should return total size of cached entries")
        void shouldReturnTotalSizeOfCachedEntries() {
            // Arrange
            featureStore.putUserFeatures("user1", createUserFeatures("user1"));
            featureStore.putUserFeatures("user2", createUserFeatures("user2"));
            featureStore.putProblemFeatures(1L, createProblemFeatures(1L));
            featureStore.putProblemFeatures(2L, createProblemFeatures(2L));
            featureStore.putProblemFeatures(3L, createProblemFeatures(3L));

            // Act & Assert
            assertEquals(5, featureStore.size()); // 2 users + 3 problems
        }

        @Test
        @DisplayName("Should throw exception for null userId in invalidateUser")
        void shouldThrowExceptionForNullUserIdInInvalidateUser() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.invalidateUser(null));
        }

        @Test
        @DisplayName("Should throw exception for null problemId in invalidateProblem")
        void shouldThrowExceptionForNullProblemIdInInvalidateProblem() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                featureStore.invalidateProblem(null));
        }

        @Test
        @DisplayName("Should handle invalidating non-existent user gracefully")
        void shouldHandleInvalidatingNonExistentUserGracefully() {
            // Act - should not throw
            assertDoesNotThrow(() -> featureStore.invalidateUser("nonexistent"));
        }

        @Test
        @DisplayName("Should handle invalidating non-existent problem gracefully")
        void shouldHandleInvalidatingNonExistentProblemGracefully() {
            // Act - should not throw
            assertDoesNotThrow(() -> featureStore.invalidateProblem(999L));
        }
    }

    // ==================== Statistics Tests ====================

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should return correct user count")
        void shouldReturnCorrectUserCount() {
            // Arrange
            featureStore.putUserFeatures("user1", createUserFeatures("user1"));
            featureStore.putUserFeatures("user2", createUserFeatures("user2"));

            // Act & Assert
            assertEquals(2, featureStore.getUserCount());
        }

        @Test
        @DisplayName("Should return correct problem count")
        void shouldReturnCorrectProblemCount() {
            // Arrange
            featureStore.putProblemFeatures(1L, createProblemFeatures(1L));
            featureStore.putProblemFeatures(2L, createProblemFeatures(2L));
            featureStore.putProblemFeatures(3L, createProblemFeatures(3L));

            // Act & Assert
            assertEquals(3, featureStore.getProblemCount());
        }

        @Test
        @DisplayName("Should calculate hit rate correctly")
        void shouldCalculateHitRateCorrectly() {
            // Arrange
            featureStore.putUserFeatures("user1", createUserFeatures("user1"));
            featureStore.putProblemFeatures(1L, createProblemFeatures(1L));

            // Act - 2 hits
            featureStore.getUserFeatures("user1");
            featureStore.getProblemFeatures(1L);

            // 2 misses
            featureStore.getUserFeatures("nonexistent");
            featureStore.getProblemFeatures(999L);

            // Assert
            double hitRate = featureStore.getHitRate();
            assertEquals(0.5, hitRate, 0.001); // 2 hits / 4 total = 0.5
        }

        @Test
        @DisplayName("Should return 0 hit rate when no requests made")
        void shouldReturnZeroHitRateWhenNoRequestsMade() {
            // Act & Assert
            assertEquals(0.0, featureStore.getHitRate(), 0.001);
        }

        @Test
        @DisplayName("Should return 1.0 hit rate when all requests are hits")
        void shouldReturnOneHitRateWhenAllRequestsAreHits() {
            // Arrange
            featureStore.putUserFeatures("user1", createUserFeatures("user1"));

            // Act
            featureStore.getUserFeatures("user1");
            featureStore.getUserFeatures("user1");

            // Assert
            assertEquals(1.0, featureStore.getHitRate(), 0.001);
        }

        @Test
        @DisplayName("Should return 0.0 hit rate when all requests are misses")
        void shouldReturnZeroHitRateWhenAllRequestsAreMisses() {
            // Act - only misses
            featureStore.getUserFeatures("nonexistent1");
            featureStore.getUserFeatures("nonexistent2");
            featureStore.getProblemFeatures(999L);

            // Assert
            assertEquals(0.0, featureStore.getHitRate(), 0.001);
        }

        @Test
        @DisplayName("Should count batch operations in statistics")
        void shouldCountBatchOperationsInStatistics() {
            // Arrange
            Map<String, UserFeatures> userFeaturesMap = new HashMap<>();
            userFeaturesMap.put("user1", createUserFeatures("user1"));
            userFeaturesMap.put("user2", createUserFeatures("user2"));
            featureStore.putAllUserFeatures(userFeaturesMap);

            Set<String> userIds = Set.of("user1", "user2", "user3");
            featureStore.getAllUserFeatures(userIds);

            // Act
            double hitRate = featureStore.getHitRate();

            // Assert - 2 hits (user1, user2) / 3 requests = 0.667
            assertEquals(2.0/3.0, hitRate, 0.01);
        }
    }

    // ==================== Thread Safety Tests ====================

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent user feature writes")
        void shouldHandleConcurrentUserFeatureWrites() throws InterruptedException {
            // Arrange
            int numThreads = 10;
            int numOperations = 100;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger errors = new AtomicInteger(0);

            // Act
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < numOperations; j++) {
                            String userId = "user_" + threadId + "_" + j;
                            UserFeatures features = createUserFeatures(userId);
                            featureStore.putUserFeatures(userId, features);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Assert
            assertEquals(0, errors.get());
            assertEquals(numThreads * numOperations, featureStore.getUserCount());
        }

        @Test
        @DisplayName("Should handle concurrent reads and writes")
        void shouldHandleConcurrentReadsAndWrites() throws InterruptedException {
            // Arrange
            int numThreads = 10;
            int numOperations = 50;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads * 2);
            AtomicInteger errors = new AtomicInteger(0);

            // Pre-populate some data
            for (int i = 0; i < 10; i++) {
                featureStore.putUserFeatures("existing_" + i, createUserFeatures("existing_" + i));
            }

            // Act - Writers
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < numOperations; j++) {
                            String userId = "user_" + threadId + "_" + j;
                            featureStore.putUserFeatures(userId, createUserFeatures(userId));
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Act - Readers
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < numOperations; j++) {
                            featureStore.getUserFeatures("existing_" + (j % 10));
                            featureStore.getUserFeatures("user_" + threadId + "_" + j);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Assert
            assertEquals(0, errors.get());
        }

        @Test
        @DisplayName("Should handle concurrent invalidation")
        void shouldHandleConcurrentInvalidation() throws InterruptedException {
            // Arrange
            int numThreads = 5;
            int numOperations = 20;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads * 3);
            AtomicInteger errors = new AtomicInteger(0);

            // Pre-populate
            for (int i = 0; i < 100; i++) {
                featureStore.putUserFeatures("user_" + i, createUserFeatures("user_" + i));
            }

            // Act - Writers
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < numOperations; j++) {
                            String userId = "user_" + j;
                            featureStore.putUserFeatures(userId, createUserFeatures(userId));
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Act - Readers
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < numOperations; j++) {
                            featureStore.getUserFeatures("user_" + j);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Act - Invalidators
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < numOperations; j++) {
                            featureStore.invalidateUser("user_" + j);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Assert - no exceptions thrown
            assertEquals(0, errors.get());
        }
    }

    // ==================== Helper Methods ====================

    private UserFeatures createUserFeatures(String userId) {
        return createUserFeatures(userId, 0.5);
    }

    private UserFeatures createUserFeatures(String userId, double activityLevel) {
        return UserFeatures.builder()
            .userId(userId)
            .activityLevel(activityLevel)
            .totalSubmissions(100)
            .recentSubmissions(10)
            .easySuccessRate(0.8)
            .mediumSuccessRate(0.6)
            .hardSuccessRate(0.3)
            .skillLevel("intermediate")
            .tagPreferences(Map.of("array", 0.5, "dp", 0.3))
            .tagMastery(Map.of("array", 0.7, "dp", 0.4))
            .strongTags(Set.of("array"))
            .weakTags(Set.of("dp"))
            .learningVelocity(0.1)
            .streakDays(5)
            .consistency(0.7)
            .build();
    }

    private ProblemFeatures createProblemFeatures(Long problemId) {
        return createProblemFeatures(problemId, 0.5);
    }

    private ProblemFeatures createProblemFeatures(Long problemId, double difficultyScore) {
        return ProblemFeatures.builder()
            .problemId(problemId)
            .slug("problem-" + problemId)
            .title("Problem " + problemId)
            .difficulty("Medium")
            .difficultyScore(difficultyScore)
            .tags(Set.of("array", "dynamic-programming"))
            .categories(Set.of("algorithm", "data-structure"))
            .tagWeights(Map.of("array", 0.8, "dynamic-programming", 0.6))
            .acceptanceRate(0.5)
            .totalSubmissions(1000)
            .acceptedSubmissions(500)
            .qualityScore(0.6)
            .likes(100)
            .dislikes(20)
            .popularityScore(0.83)
            .avgSimilarity(0.4)
            .similarProblems(Set.of(2L, 3L))
            .build();
    }
}

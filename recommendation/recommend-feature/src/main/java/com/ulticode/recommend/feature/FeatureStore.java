package com.ulticode.recommend.feature;

import com.ulticode.recommend.feature.model.ProblemFeatures;
import com.ulticode.recommend.feature.model.UserFeatures;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe cache for storing and retrieving extracted features.
 *
 * <p>This store provides:
 * <ul>
 *   <li>Storage operations for user and problem features</li>
 *   <li>Batch operations for efficient bulk processing</li>
 *   <li>Cache management (invalidation, clearing)</li>
 *   <li>Statistics tracking (counts, hit rate)</li>
 *   <li>TTL-based eviction for data freshness</li>
 * </ul>
 *
 * <p>Implementation uses ConcurrentHashMap for thread-safe operations.
 * Default TTL is 5 minutes (300,000 milliseconds).
 */
public class FeatureStore {

    /**
     * Default TTL for cache entries in milliseconds (5 minutes).
     */
    private static final long DEFAULT_TTL_MS = 300_000L;

    private final ConcurrentHashMap<String, CacheEntry<UserFeatures>> userFeaturesCache;
    private final ConcurrentHashMap<Long, CacheEntry<ProblemFeatures>> problemFeaturesCache;
    private final AtomicLong hits;
    private final AtomicLong misses;
    private final long ttlMs;

    /**
     * Internal cache entry that stores the value along with creation timestamp.
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long createdAt;

        CacheEntry(T value) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - createdAt > ttlMs;
        }

        T getValue() {
            return value;
        }
    }

    /**
     * Creates a new empty FeatureStore with default TTL (5 minutes).
     */
    public FeatureStore() {
        this(DEFAULT_TTL_MS);
    }

    /**
     * Creates a new empty FeatureStore with custom TTL.
     *
     * @param ttlMs time-to-live in milliseconds for cache entries
     */
    public FeatureStore(long ttlMs) {
        this.userFeaturesCache = new ConcurrentHashMap<>();
        this.problemFeaturesCache = new ConcurrentHashMap<>();
        this.hits = new AtomicLong(0);
        this.misses = new AtomicLong(0);
        this.ttlMs = ttlMs;
    }

    // ==================== User Features Storage ====================

    /**
     * Stores user features in the cache.
     *
     * @param userId  the user ID, must not be null
     * @param features the user features, must not be null
     * @throws IllegalArgumentException if userId or features is null
     */
    public void putUserFeatures(String userId, UserFeatures features) {
        validateUserId(userId);
        validateUserFeatures(features);
        userFeaturesCache.put(userId, new CacheEntry<>(features));
    }

    /**
     * Retrieves user features from the cache.
     *
     * <p>Automatically evicts expired entries.
     *
     * @param userId the user ID, must not be null
     * @return an Optional containing the features if found and not expired, empty otherwise
     * @throws IllegalArgumentException if userId is null
     */
    public Optional<UserFeatures> getUserFeatures(String userId) {
        validateUserId(userId);
        CacheEntry<UserFeatures> entry = userFeaturesCache.get(userId);
        if (entry != null) {
            if (entry.isExpired(ttlMs)) {
                userFeaturesCache.remove(userId);
                misses.incrementAndGet();
                return Optional.empty();
            }
            hits.incrementAndGet();
            return Optional.of(entry.getValue());
        }
        misses.incrementAndGet();
        return Optional.empty();
    }

    // ==================== Problem Features Storage ====================

    /**
     * Stores problem features in the cache.
     *
     * @param problemId the problem ID, must not be null
     * @param features  the problem features, must not be null
     * @throws IllegalArgumentException if problemId or features is null
     */
    public void putProblemFeatures(Long problemId, ProblemFeatures features) {
        validateProblemId(problemId);
        validateProblemFeatures(features);
        problemFeaturesCache.put(problemId, new CacheEntry<>(features));
    }

    /**
     * Retrieves problem features from the cache.
     *
     * <p>Automatically evicts expired entries.
     *
     * @param problemId the problem ID, must not be null
     * @return an Optional containing the features if found and not expired, empty otherwise
     * @throws IllegalArgumentException if problemId is null
     */
    public Optional<ProblemFeatures> getProblemFeatures(Long problemId) {
        validateProblemId(problemId);
        CacheEntry<ProblemFeatures> entry = problemFeaturesCache.get(problemId);
        if (entry != null) {
            if (entry.isExpired(ttlMs)) {
                problemFeaturesCache.remove(problemId);
                misses.incrementAndGet();
                return Optional.empty();
            }
            hits.incrementAndGet();
            return Optional.of(entry.getValue());
        }
        misses.incrementAndGet();
        return Optional.empty();
    }

    // ==================== Batch Operations ====================

    /**
     * Stores multiple user features at once.
     *
     * @param featuresMap map of user IDs to features, must not be null
     * @throws IllegalArgumentException if featuresMap is null
     */
    public void putAllUserFeatures(Map<String, UserFeatures> featuresMap) {
        if (featuresMap == null) {
            throw new IllegalArgumentException("featuresMap must not be null");
        }
        featuresMap.forEach((userId, features) -> {
            if (userId != null && features != null) {
                userFeaturesCache.put(userId, new CacheEntry<>(features));
            }
        });
    }

    /**
     * Retrieves multiple user features at once.
     * Only returns features for users that exist in the cache and are not expired.
     * Expired entries are automatically removed.
     *
     * @param userIds set of user IDs to retrieve, must not be null
     * @return map of user IDs to features (only for existing and valid users)
     * @throws IllegalArgumentException if userIds is null
     */
    public Map<String, UserFeatures> getAllUserFeatures(Set<String> userIds) {
        if (userIds == null) {
            throw new IllegalArgumentException("userIds must not be null");
        }
        Map<String, UserFeatures> result = new HashMap<>();
        for (String userId : userIds) {
            if (userId != null) {
                CacheEntry<UserFeatures> entry = userFeaturesCache.get(userId);
                if (entry != null) {
                    if (entry.isExpired(ttlMs)) {
                        userFeaturesCache.remove(userId);
                        misses.incrementAndGet();
                    } else {
                        hits.incrementAndGet();
                        result.put(userId, entry.getValue());
                    }
                } else {
                    misses.incrementAndGet();
                }
            }
        }
        return result;
    }

    /**
     * Stores multiple problem features at once.
     *
     * @param featuresMap map of problem IDs to features, must not be null
     * @throws IllegalArgumentException if featuresMap is null
     */
    public void putAllProblemFeatures(Map<Long, ProblemFeatures> featuresMap) {
        if (featuresMap == null) {
            throw new IllegalArgumentException("featuresMap must not be null");
        }
        featuresMap.forEach((problemId, features) -> {
            if (problemId != null && features != null) {
                problemFeaturesCache.put(problemId, new CacheEntry<>(features));
            }
        });
    }

    /**
     * Retrieves multiple problem features at once.
     * Only returns features for problems that exist in the cache and are not expired.
     * Expired entries are automatically removed.
     *
     * @param problemIds set of problem IDs to retrieve, must not be null
     * @return map of problem IDs to features (only for existing and valid problems)
     * @throws IllegalArgumentException if problemIds is null
     */
    public Map<Long, ProblemFeatures> getAllProblemFeatures(Set<Long> problemIds) {
        if (problemIds == null) {
            throw new IllegalArgumentException("problemIds must not be null");
        }
        Map<Long, ProblemFeatures> result = new HashMap<>();
        for (Long problemId : problemIds) {
            if (problemId != null) {
                CacheEntry<ProblemFeatures> entry = problemFeaturesCache.get(problemId);
                if (entry != null) {
                    if (entry.isExpired(ttlMs)) {
                        problemFeaturesCache.remove(problemId);
                        misses.incrementAndGet();
                    } else {
                        hits.incrementAndGet();
                        result.put(problemId, entry.getValue());
                    }
                } else {
                    misses.incrementAndGet();
                }
            }
        }
        return result;
    }

    // ==================== Cache Management ====================

    /**
     * Removes a user from the cache.
     *
     * @param userId the user ID to remove, must not be null
     * @throws IllegalArgumentException if userId is null
     */
    public void invalidateUser(String userId) {
        validateUserId(userId);
        userFeaturesCache.remove(userId);
    }

    /**
     * Removes a problem from the cache.
     *
     * @param problemId the problem ID to remove, must not be null
     * @throws IllegalArgumentException if problemId is null
     */
    public void invalidateProblem(Long problemId) {
        validateProblemId(problemId);
        problemFeaturesCache.remove(problemId);
    }

    /**
     * Clears all cached data.
     * Also resets hit/miss statistics.
     */
    public void clear() {
        userFeaturesCache.clear();
        problemFeaturesCache.clear();
        hits.set(0);
        misses.set(0);
    }

    /**
     * Returns the total number of cached entries.
     *
     * @return sum of user and problem feature counts
     */
    public int size() {
        return userFeaturesCache.size() + problemFeaturesCache.size();
    }

    /**
     * Removes all expired entries from the cache.
     * This is a maintenance operation that can be called periodically.
     *
     * @return the number of entries removed
     */
    public int cleanupExpired() {
        int removed = 0;

        // Clean up expired user features
        removed += (int) userFeaturesCache.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired(ttlMs))
                .peek(entry -> userFeaturesCache.remove(entry.getKey()))
                .count();

        // Clean up expired problem features
        removed += (int) problemFeaturesCache.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired(ttlMs))
                .peek(entry -> problemFeaturesCache.remove(entry.getKey()))
                .count();

        return removed;
    }

    /**
     * Returns the configured TTL for cache entries.
     *
     * @return TTL in milliseconds
     */
    public long getTtlMs() {
        return ttlMs;
    }

    // ==================== Statistics ====================

    /**
     * Returns the number of cached users.
     *
     * @return user count
     */
    public int getUserCount() {
        return userFeaturesCache.size();
    }

    /**
     * Returns the number of cached problems.
     *
     * @return problem count
     */
    public int getProblemCount() {
        return problemFeaturesCache.size();
    }

    /**
     * Returns the cache hit rate.
     *
     * <p>Hit rate is calculated as: hits / (hits + misses)
     *
     * @return hit rate between 0.0 and 1.0, or 0.0 if no requests made
     */
    public double getHitRate() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        if (total == 0) {
            return 0.0;
        }
        return (double) totalHits / total;
    }

    // ==================== Validation Helpers ====================

    private void validateUserId(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }

    private void validateUserFeatures(UserFeatures features) {
        if (features == null) {
            throw new IllegalArgumentException("features must not be null");
        }
    }

    private void validateProblemId(Long problemId) {
        if (problemId == null) {
            throw new IllegalArgumentException("problemId must not be null");
        }
    }

    private void validateProblemFeatures(ProblemFeatures features) {
        if (features == null) {
            throw new IllegalArgumentException("features must not be null");
        }
    }
}

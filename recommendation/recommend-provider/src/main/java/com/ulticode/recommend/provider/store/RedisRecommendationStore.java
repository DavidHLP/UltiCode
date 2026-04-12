package com.ulticode.recommend.provider.store;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads pre-computed recommendation data from Redis.
 *
 * <p>Redis keys (written by backend-spring sync and Spark offline jobs):
 * <ul>
 *   <li>{@code recommend:available:problems} — available problems list (JSON)</li>
 *   <li>{@code recommend:user:problem:matrix} — user→solvedProblems mapping (JSON)</li>
 *   <li>{@code recommend:user:profiles} — user profile data (rating, maxRating, preferredLanguage)</li>
 *   <li>{@code recommend:similar:problems:{problemId}} — similar problems for a given problem</li>
 * </ul>
 */
@Component
public class RedisRecommendationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRecommendationStore.class);

    private static final String AVAILABLE_PROBLEMS_KEY = "recommend:available:problems";
    private static final String USER_PROBLEM_MATRIX_KEY = "recommend:user:problem:matrix";
    private static final String USER_PROFILES_KEY = "recommend:user:profiles";
    private static final String SIMILAR_PROBLEMS_PREFIX = "recommend:similar:problems:";
    private static final int MAX_PAYLOAD_BYTES = 10 * 1024 * 1024; // 10MB safety limit

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisRecommendationStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Load available problems list from Redis.
     * Falls back to empty list if Redis has no data.
     */
    public List<RecommendItem> loadAvailableProblems() {
        return loadList(AVAILABLE_PROBLEMS_KEY, new TypeReference<List<RecommendItem>>() {});
    }

    /**
     * Load user-problem matrix from Redis.
     * Falls back to empty map if Redis has no data.
     */
    public Map<String, Set<Long>> loadUserProblemMatrix() {
        return loadMap(USER_PROBLEM_MATRIX_KEY, new TypeReference<Map<String, Set<Long>>>() {});
    }

    /**
     * Load similar problem IDs for a given problem.
     */
    public List<Long> loadSimilarProblemIds(long problemId) {
        return loadList(SIMILAR_PROBLEMS_PREFIX + problemId, new TypeReference<List<Long>>() {});
    }

    /**
     * Load user profile data (rating, maxRating, preferredLanguage) from Redis.
     * Returns null if the user has no profile data in Redis.
     *
     * <p>Redis key: {@code recommend:user:profiles} — JSON map of userId to profile fields.
     * Written by backend-spring RecommendationDataService.syncUserProfiles().
     */
    public UserProfile loadUserProfile(String userId) {
        Map<String, Map<String, Object>> profiles = loadMap(
                USER_PROFILES_KEY,
                new TypeReference<Map<String, Map<String, Object>>>() {});
        if (profiles.isEmpty() || !profiles.containsKey(userId)) {
            return null;
        }
        Map<String, Object> fields = profiles.get(userId);

        Set<Long> solvedProblems = loadSolvedProblemsForUser(userId);

        return UserProfile.builder()
                .userId(userId)
                .rating(((Number) fields.getOrDefault("rating", 1500)).intValue())
                .maxRating(((Number) fields.getOrDefault("maxRating", 1500)).intValue())
                .preferredLanguage((String) fields.get("preferredLanguage"))
                .solvedProblems(solvedProblems)
                .totalSolved(solvedProblems.size())
                .build();
    }

    /**
     * Load solved problem IDs for a specific user from the user-problem matrix.
     * Falls back to empty set if Redis has no data for this user.
     */
    public Set<Long> loadSolvedProblemsForUser(String userId) {
        Map<String, Set<Long>> matrix = loadUserProblemMatrix();
        return matrix.getOrDefault(userId, Set.of());
    }

    // ==================== Generic Helpers ====================

    private <T> List<T> loadList(String key, TypeReference<List<T>> typeRef) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                log.debug("Redis key '{}' not found, returning empty list", key);
                return List.of();
            }
            if (json.length() > MAX_PAYLOAD_BYTES) {
                log.warn("Redis key '{}' payload too large ({} bytes), returning empty list", key, json.length());
                return List.of();
            }
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Redis key '{}': {}", key, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("Redis unavailable when reading key '{}', returning empty list: {}", key, e.getMessage());
            return List.of();
        }
    }

    private <T> Map<String, T> loadMap(String key, TypeReference<Map<String, T>> typeRef) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                log.debug("Redis key '{}' not found, returning empty map", key);
                return Map.of();
            }
            if (json.length() > MAX_PAYLOAD_BYTES) {
                log.warn("Redis key '{}' payload too large ({} bytes), returning empty map", key, json.length());
                return Map.of();
            }
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Redis key '{}': {}", key, e.getMessage());
            return Map.of();
        } catch (Exception e) {
            log.warn("Redis unavailable when reading key '{}', returning empty map: {}", key, e.getMessage());
            return Map.of();
        }
    }
}

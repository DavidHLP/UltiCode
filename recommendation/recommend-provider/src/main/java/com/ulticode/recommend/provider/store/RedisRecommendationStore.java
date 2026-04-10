package com.ulticode.recommend.provider.store;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.recommend.core.model.RecommendItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads pre-computed recommendation data from Redis.
 *
 * <p>Redis keys (written by Spark offline jobs):
 * <ul>
 *   <li>{@code rec:problems} — available problems list (JSON)</li>
 *   <li>{@code rec:user-matrix} — user→solvedProblems mapping (JSON)</li>
 *   <li>{@code rec:similarity:{problemId}} — similar problems for a given problem</li>
 *   <li>{@code rec:feature:user:{userId}} — user feature profile (JSON)</li>
 *   <li>{@code rec:feature:problem:{problemId}} — problem features (JSON)</li>
 * </ul>
 */
@Component
public class RedisRecommendationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRecommendationStore.class);

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
        return loadList("rec:problems", new TypeReference<List<RecommendItem>>() {});
    }

    /**
     * Load user-problem matrix from Redis.
     * Falls back to empty map if Redis has no data.
     */
    public Map<String, Set<Long>> loadUserProblemMatrix() {
        return loadMap("rec:user-matrix", new TypeReference<Map<String, Set<Long>>>() {});
    }

    /**
     * Load similar problem IDs for a given problem.
     */
    public List<Long> loadSimilarProblemIds(long problemId) {
        return loadList("rec:similarity:" + problemId, new TypeReference<List<Long>>() {});
    }

    // ==================== Generic Helpers ====================

    private <T> List<T> loadList(String key, TypeReference<List<T>> typeRef) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                log.debug("Redis key '{}' not found, returning empty list", key);
                return List.of();
            }
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Redis key '{}': {}", key, e.getMessage());
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
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Redis key '{}': {}", key, e.getMessage());
            return Map.of();
        }
    }
}

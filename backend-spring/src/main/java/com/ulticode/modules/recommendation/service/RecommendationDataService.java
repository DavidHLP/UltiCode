package com.ulticode.modules.recommendation.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.entity.GlobalRanking;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for syncing recommendation data from MySQL to Redis.
 * Provides the data foundation that recommend-provider reads via Dubbo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationDataService {

    private final ProblemMapper problemMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemTagMapper problemTagMapper;
    private final SubmissionMapper submissionMapper;
    private final GlobalRankingMapper globalRankingMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String AVAILABLE_PROBLEMS_KEY = "recommend:available:problems";
    private static final String USER_PROBLEM_MATRIX_KEY = "recommend:user:problem:matrix";
    private static final String USER_PROFILES_KEY = "recommend:user:profiles";
    private static final String SIMILAR_PROBLEMS_PREFIX = "recommend:similar:problems:";

    /**
     * Sync all published problems with tags to Redis.
     *
     * @return number of problems synced
     */
    public int syncAvailableProblems() {
        List<Problem> problems = problemMapper.selectList(
                new QueryWrapper<Problem>()
                        .eq("is_deleted", false)
                        .eq("is_published", true)
        );

        List<Map<String, Object>> problemItems = new ArrayList<>();
        for (Problem problem : problems) {
            List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(problem.getId());
            List<String> tagLabels = new ArrayList<>();
            if (!tagIds.isEmpty()) {
                List<ProblemTag> tags = problemTagMapper.selectBatchIds(tagIds);
                tagLabels = tags.stream().map(ProblemTag::getLabel).collect(Collectors.toList());
            }

            Map<String, Object> item = new HashMap<>();
            item.put("problemId", problem.getId());
            item.put("slug", problem.getSlug());
            item.put("title", problem.getTitle());
            item.put("difficulty", problem.getDifficulty());
            item.put("score", 0.5 + Math.random() * 0.5);
            item.put("tags", tagLabels);
            item.put("reason", "推荐练习");
            item.put("qualityScore", problem.getAcceptanceRate() != null
                    ? problem.getAcceptanceRate().doubleValue() / 100.0 : 0.5);
            problemItems.add(item);
        }

        try {
            String json = objectMapper.writeValueAsString(problemItems);
            redisTemplate.opsForValue().set(AVAILABLE_PROBLEMS_KEY, json);
            log.info("Synced {} available problems to Redis", problemItems.size());
            return problemItems.size();
        // broad catch: data collection failure -- log and use defaults
        } catch (Exception e) {
            log.error("Failed to sync available problems to Redis", e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Redis sync failed for available problems", e);
        }
    }

    /**
     * Build user → solved-problems matrix from accepted submissions and sync to Redis.
     *
     * @return number of users in the matrix
     */
    public int syncUserProblemMatrix() {
        List<Submission> acceptedSubmissions = submissionMapper.selectList(
                new QueryWrapper<Submission>()
                        .eq("status", "Accepted")
                        .select("DISTINCT user_id, problem_id")
        );

        Map<String, Set<Long>> userMatrix = new HashMap<>();
        for (Submission sub : acceptedSubmissions) {
            userMatrix
                    .computeIfAbsent(sub.getUserId(), k -> new HashSet<>())
                    .add(sub.getProblemId());
        }

        try {
            String json = objectMapper.writeValueAsString(userMatrix);
            redisTemplate.opsForValue().set(USER_PROBLEM_MATRIX_KEY, json);
            log.info("Synced user-problem matrix for {} users to Redis", userMatrix.size());
            return userMatrix.size();
        // broad catch: data collection failure -- log and use defaults
        } catch (Exception e) {
            log.error("Failed to sync user-problem matrix to Redis", e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Redis sync failed for user-problem matrix", e);
        }
    }

    /**
     * Sync user profiles (rating, maxRating, preferredLanguage) from global_rankings + users to Redis.
     *
     * @return number of user profiles synced
     */
    public int syncUserProfiles() {
        List<GlobalRanking> rankings = globalRankingMapper.selectList(null);

        Map<String, Map<String, Object>> profiles = new HashMap<>();
        for (GlobalRanking ranking : rankings) {
            Map<String, Object> profile = new HashMap<>();
            profile.put("rating", ranking.getRating() != null ? ranking.getRating() : 1500);
            profile.put("maxRating", ranking.getMaxRating() != null ? ranking.getMaxRating() : 1500);

            // Load preferred language from users table
            User user = userMapper.selectById(ranking.getUserId());
            if (user != null && user.getPreferredLanguage() != null) {
                profile.put("preferredLanguage", user.getPreferredLanguage());
            }

            profiles.put(ranking.getUserId(), profile);
        }

        try {
            String json = objectMapper.writeValueAsString(profiles);
            redisTemplate.opsForValue().set(USER_PROFILES_KEY, json);
            log.info("Synced user profiles for {} users to Redis", profiles.size());
            return profiles.size();
        // broad catch: data collection failure -- log and use defaults
        } catch (Exception e) {
            log.error("Failed to sync user profiles to Redis", e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Redis sync failed for user profiles", e);
        }
    }

    /**
     * Compute similar problems by tag overlap (Jaccard similarity) and sync to Redis.
     *
     * @return number of problems with similar entries
     */
    public int syncSimilarProblems() {
        List<Problem> problems = problemMapper.selectList(
                new QueryWrapper<Problem>()
                        .eq("is_deleted", false)
                        .eq("is_published", true)
                        .select("id")
        );

        Map<Long, Set<String>> problemTags = new HashMap<>();
        for (Problem p : problems) {
            List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(p.getId());
            problemTags.put(p.getId(), new HashSet<>(tagIds));
        }

        int totalEntries = 0;
        for (Problem p : problems) {
            Set<String> tagsA = problemTags.getOrDefault(p.getId(), Set.of());
            if (tagsA.isEmpty()) continue;

            Map<Long, Double> scores = new HashMap<>();
            for (Problem other : problems) {
                if (other.getId().equals(p.getId())) continue;
                Set<String> tagsB = problemTags.getOrDefault(other.getId(), Set.of());
                if (tagsB.isEmpty()) continue;

                Set<String> intersection = new HashSet<>(tagsA);
                intersection.retainAll(tagsB);
                Set<String> union = new HashSet<>(tagsA);
                union.addAll(tagsB);

                double jaccard = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
                if (jaccard > 0.1) {
                    scores.put(other.getId(), jaccard);
                }
            }

            List<Long> similar = scores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (!similar.isEmpty()) {
                try {
                    String key = SIMILAR_PROBLEMS_PREFIX + p.getId();
                    redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(similar));
                    totalEntries++;
                // broad catch: data collection failure -- log and use defaults
                } catch (Exception e) {
                    log.error("Failed to sync similar problems for problem {}", p.getId(), e);
                }
            }
        }

        log.info("Synced similar problems for {} problems to Redis", totalEntries);
        return totalEntries;
    }

    /**
     * Execute all sync operations in sequence.
     *
     * @return summary map with counts
     */
    public Map<String, Object> syncAll() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("problemsSynced", syncAvailableProblems());
        stats.put("userMatrixEntries", syncUserProblemMatrix());
        stats.put("userProfilesSynced", syncUserProfiles());
        stats.put("similarProblemEntries", syncSimilarProblems());
        return stats;
    }

    /**
     * Clear all recommendation data from Redis.
     */
    public void clearAll() {
        redisTemplate.delete(AVAILABLE_PROBLEMS_KEY);
        redisTemplate.delete(USER_PROBLEM_MATRIX_KEY);
        redisTemplate.delete(USER_PROFILES_KEY);

        Set<String> similarKeys = redisTemplate.keys(SIMILAR_PROBLEMS_PREFIX + "*");
        if (similarKeys != null && !similarKeys.isEmpty()) {
            redisTemplate.delete(similarKeys);
        }

        log.info("Cleared all recommendation data from Redis");
    }
}

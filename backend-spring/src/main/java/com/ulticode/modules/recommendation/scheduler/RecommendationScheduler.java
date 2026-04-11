package com.ulticode.modules.recommendation.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.recommendation.config.RecommendationConfig;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.entity.DailyRecommendation;
import com.ulticode.modules.recommendation.mapper.DailyRecommendationMapper;
import com.ulticode.modules.recommendation.service.RecommendationDataService;
import com.ulticode.modules.recommendation.service.RecommendationService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.recommend.api.RecommendService;
import com.ulticode.recommend.api.dto.RecommendRequest;
import com.ulticode.recommend.api.dto.RecommendResponse;
import com.ulticode.recommend.api.dto.RecommendResult;
import com.ulticode.recommend.api.enums.RecommendScenario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for recommendation data sync and daily recommendation pre-generation.
 * <p>
 * Execution order (daily):
 * 1. 03:00 — Cleanup expired recommendations (already implemented)
 * 2. 04:00 — Sync MySQL data to Redis (new)
 * 3. 06:00 — Pre-generate daily recommendations (new)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommendation", name = "enabled", havingValue = "true")
public class RecommendationScheduler {

    private final RecommendationService recommendationService;
    private final RecommendationConfig recommendationConfig;
    private final DailyRecommendationMapper dailyRecommendationMapper;
    private final RecommendationDataService recommendationDataService;
    private final SubmissionMapper submissionMapper;

    @DubboReference(check = false, timeout = 5000, retries = 1)
    private RecommendService recommendService;

    /**
     * Sync all recommendation data from MySQL to Redis.
     * Runs daily at 4:00 AM (configurable via recommendation.sync-cron).
     * This ensures the recommend-provider has fresh data to read.
     */
    @Scheduled(cron = "${recommendation.sync-cron:0 0 4 * * ?}")
    public void syncRedisData() {
        log.info("Starting scheduled Redis data sync...");
        try {
            Map<String, Object> stats = recommendationDataService.syncAll();
            log.info("Scheduled Redis data sync completed: {}", stats);
        } catch (Exception e) {
            log.error("Scheduled Redis data sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate daily recommendations for active users.
     * Runs daily at 6:00 AM (configurable via recommendation.generate-cron).
     * <p>
     * Process:
     * 1. Find users active in the last 30 days
     * 2. For each user, call Dubbo for DAILY, WEAK_POINT, CHALLENGE scenarios
     * 3. Store results in daily_recommendations table
     * 4. Skip users on Dubbo failure (isolated, doesn't affect others)
     */
    @Scheduled(cron = "${recommendation.generate-cron:0 0 6 * * ?}")
    public void generateDailyRecommendations() {
        if (!recommendationConfig.isEnabled()) {
            log.debug("Recommendation service is disabled, skipping scheduled job");
            return;
        }

        log.info("Starting daily recommendation generation job");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(recommendationConfig.getRecommendationTtlDays());
        int batchSize = recommendationConfig.getGenerateBatchSize();

        List<String> activeUsers = findActiveUsers(30);
        log.info("Found {} active users for recommendation generation", activeUsers.size());

        int totalGenerated = 0;
        int failedUsers = 0;

        for (int i = 0; i < activeUsers.size(); i += batchSize) {
            List<String> batch = activeUsers.subList(i, Math.min(i + batchSize, activeUsers.size()));
            log.info("Processing batch {}/{} ({} users)", (i / batchSize) + 1,
                    (activeUsers.size() + batchSize - 1) / batchSize, batch.size());

            for (String userId : batch) {
                try {
                    int generated = generateForUser(userId, now, expiresAt);
                    totalGenerated += generated;
                } catch (Exception e) {
                    failedUsers++;
                    log.warn("Failed to generate recommendations for user {}: {}", userId, e.getMessage());
                }
            }
        }

        log.info("Daily recommendation generation completed: {} recommendations for {} users, {} failures",
                totalGenerated, activeUsers.size() - failedUsers, failedUsers);
    }

    /**
     * Clean up expired recommendations.
     * Runs every day at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredRecommendations() {
        if (!recommendationConfig.isEnabled()) {
            return;
        }

        log.info("Starting expired recommendation cleanup job");

        try {
            int deleted = dailyRecommendationMapper.deleteExpired(LocalDateTime.now());
            log.info("Expired recommendation cleanup completed: {} entries removed", deleted);
        } catch (Exception e) {
            log.error("Error during recommendation cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate recommendations for a single user across 3 scenarios.
     */
    private int generateForUser(String userId, LocalDateTime generatedAt, LocalDateTime expiresAt) {
        int total = 0;
        RecommendScenario[] scenarios = {RecommendScenario.DAILY, RecommendScenario.WEAK_POINT, RecommendScenario.CHALLENGE};

        for (RecommendScenario scenario : scenarios) {
            try {
                RecommendRequest request = RecommendRequest.builder()
                        .userId(userId)
                        .scenario(scenario)
                        .size(10)
                        .build();

                RecommendResponse<RecommendResult> response = recommendService.recommend(request);

                if (response.isSuccess() && response.getData() != null && response.getData().getItems() != null) {
                    for (var item : response.getData().getItems()) {
                        DailyRecommendation rec = new DailyRecommendation();
                        rec.setUserId(userId);
                        rec.setProblemId(item.getProblemId());
                        rec.setScenario(scenario.name());
                        rec.setScore((float) item.getScore());
                        rec.setReason(item.getReason());
                        rec.setTags(item.getTags());
                        rec.setGeneratedAt(generatedAt);
                        rec.setExpiresAt(expiresAt);
                        rec.setIsClicked(false);
                        rec.setIsSolved(false);

                        dailyRecommendationMapper.insert(rec);
                        total++;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to generate {} recommendations for user {}: {}",
                        scenario, userId, e.getMessage());
            }
        }

        return total;
    }

    /**
     * Find users who had submissions in the last N days.
     */
    private List<String> findActiveUsers(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Submission> recentSubs = submissionMapper.selectList(
                new QueryWrapper<Submission>()
                        .ge("created_at", since)
                        .select("DISTINCT user_id")
        );

        return recentSubs.stream()
                .map(Submission::getUserId)
                .distinct()
                .toList();
    }
}

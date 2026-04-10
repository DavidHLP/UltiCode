package com.ulticode.modules.recommendation.scheduler;

import com.ulticode.modules.recommendation.config.RecommendationConfig;
import com.ulticode.modules.recommendation.mapper.DailyRecommendationMapper;
import com.ulticode.modules.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for generating daily recommendations.
 * Runs at 8 AM daily to pre-generate recommendations for users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommendation", name = "enabled", havingValue = "true")
public class RecommendationScheduler {

    private final RecommendationService recommendationService;
    private final RecommendationConfig recommendationConfig;
    private final DailyRecommendationMapper dailyRecommendationMapper;

    /**
     * Generate daily recommendations for all users.
     * Runs every day at 8:00 AM.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void generateDailyRecommendations() {
        if (!recommendationConfig.isEnabled()) {
            log.debug("Recommendation service is disabled, skipping scheduled job");
            return;
        }

        log.info("Starting daily recommendation generation job");

        try {
            // In a real implementation, this would:
            // 1. Fetch all active users (or batch of users)
            // 2. For each user, call the recommendation microservice
            // 3. Store the recommendations in the database

            // For now, we just log that the job ran
            log.info("Daily recommendation generation job completed successfully");

            // TODO: Implement actual recommendation generation logic
            // This would typically involve:
            // - Calling the recommendation microservice
            // - Storing results in daily_recommendations table
            // - Clearing old recommendations

        } catch (Exception e) {
            log.error("Error during daily recommendation generation: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up expired recommendations.
     * Runs every day at 3:00 AM to remove expired entries.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredRecommendations() {
        if (!recommendationConfig.isEnabled()) {
            return;
        }

        log.info("Starting expired recommendation cleanup job");

        try {
            int deleted = dailyRecommendationMapper.deleteExpired(java.time.LocalDateTime.now());
            log.info("Expired recommendation cleanup completed: {} entries removed", deleted);
        } catch (Exception e) {
            log.error("Error during recommendation cleanup: {}", e.getMessage(), e);
        }
    }
}

package com.ulticode.modules.recommendation.controller;

import java.util.Map;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.modules.recommendation.service.RecommendationDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint to seed Redis with recommendation data from MySQL.
 * Delegates to {@link RecommendationDataService} for all sync operations.
 */
@Slf4j
@RestController
@RequestMapping("/recommendations/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Recommendation Admin", description = "Admin endpoints for recommendation data management")
public class RecommendationDataController {

    private final RecommendationDataService recommendationDataService;

    @PostMapping("/seed")
    @RateLimit(key = "admin:recommendation-seed", limit = 30, period = 60)
    @Operation(summary = "Seed Redis with recommendation data from MySQL")
    public Result<Map<String, Object>> seedRecommendationData() {
        log.info("Starting recommendation data seed to Redis...");
        Map<String, Object> stats = recommendationDataService.syncAll();
        log.info("Recommendation data seed completed: {}", stats);
        return Result.success(stats);
    }

    @PostMapping("/clear")
    @RateLimit(key = "admin:recommendation-clear", limit = 30, period = 60)
    @Operation(summary = "Clear all recommendation data from Redis")
    public Result<String> clearRecommendationData() {
        log.info("Clearing recommendation data from Redis...");
        recommendationDataService.clearAll();
        return Result.success("Cleared successfully");
    }
}

package com.ulticode.modules.recommendation.controller;

import java.util.Map;

import com.ulticode.common.response.Result;
import com.ulticode.modules.recommendation.service.RecommendationDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "Recommendation Admin", description = "Admin endpoints for recommendation data management")
public class RecommendationDataController {

    private final RecommendationDataService recommendationDataService;

    @PostMapping("/seed")
    @Operation(summary = "Seed Redis with recommendation data from MySQL")
    public Result<Map<String, Object>> seedRecommendationData() {
        log.info("Starting recommendation data seed to Redis...");
        try {
            Map<String, Object> stats = recommendationDataService.syncAll();
            log.info("Recommendation data seed completed: {}", stats);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("Failed to seed recommendation data", e);
            return Result.error(500, "Seed failed: " + e.getMessage());
        }
    }

    @PostMapping("/clear")
    @Operation(summary = "Clear all recommendation data from Redis")
    public Result<String> clearRecommendationData() {
        log.info("Clearing recommendation data from Redis...");
        try {
            recommendationDataService.clearAll();
            return Result.success("Cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear recommendation data", e);
            return Result.error(500, "Clear failed: " + e.getMessage());
        }
    }
}

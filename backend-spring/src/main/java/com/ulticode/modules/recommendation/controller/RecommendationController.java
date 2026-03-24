package com.ulticode.modules.recommendation.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;
import com.ulticode.modules.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for problem recommendations.
 * Provides endpoints for getting personalized problem recommendations.
 */
@Tag(name = "Recommendation", description = "Problem recommendation endpoints")
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * Get personalized recommendations for the current user.
     *
     * @param dto the request parameters
     * @return recommendation response
     */
    @Operation(summary = "Get personalized recommendations", description = "Get personalized problem recommendations based on user's history and preferences")
    @PostMapping
    public Result<RecommendResponseVO> getRecommendations(@Valid @RequestBody GetRecommendationsDTO dto) {
        RecommendResponseVO response = recommendationService.getRecommendations(dto);
        return wrapResponse(response);
    }

    /**
     * Get daily recommendations for the current user.
     *
     * @param limit maximum number of recommendations (default 10)
     * @return recommendation response with daily practice problems
     */
    @Operation(summary = "Get daily recommendations", description = "Get daily practice recommendations for the current user")
    @GetMapping("/daily")
    public Result<RecommendResponseVO> getDailyRecommendations(
            @Parameter(description = "Maximum number of recommendations")
            @RequestParam(required = false) Integer limit) {
        RecommendResponseVO response = recommendationService.getDailyRecommendations(limit);
        return wrapResponse(response);
    }

    /**
     * Get problems similar to a given problem.
     *
     * @param problemId the problem ID to find similar problems for
     * @param limit     maximum number of recommendations (default 10)
     * @return recommendation response with similar problems
     */
    @Operation(summary = "Get similar problems", description = "Get problems similar to the specified problem")
    @GetMapping("/similar/{problemId}")
    public Result<RecommendResponseVO> getSimilarProblems(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Parameter(description = "Maximum number of recommendations")
            @RequestParam(required = false) Integer limit) {
        RecommendResponseVO response = recommendationService.getSimilarProblems(problemId, limit);
        return wrapResponse(response);
    }

    /**
     * Get weak point recommendations based on user's submission history.
     *
     * @param limit maximum number of recommendations (default 10)
     * @return recommendation response targeting user's weak areas
     */
    @Operation(summary = "Get weak point recommendations", description = "Get recommendations targeting your weak areas based on submission history")
    @GetMapping("/weak-points")
    public Result<RecommendResponseVO> getWeakPointRecommendations(
            @Parameter(description = "Maximum number of recommendations")
            @RequestParam(required = false) Integer limit) {
        RecommendResponseVO response = recommendationService.getWeakPointRecommendations(limit);
        return wrapResponse(response);
    }

    /**
     * Get challenge recommendations (harder problems).
     *
     * @param limit maximum number of recommendations (default 10)
     * @return recommendation response with challenging problems
     */
    @Operation(summary = "Get challenge recommendations", description = "Get harder problems to challenge yourself")
    @GetMapping("/challenge")
    public Result<RecommendResponseVO> getChallengeRecommendations(
            @Parameter(description = "Maximum number of recommendations")
            @RequestParam(required = false) Integer limit) {
        RecommendResponseVO response = recommendationService.getChallengeRecommendations(limit);
        return wrapResponse(response);
    }

    /**
     * Health check for the recommendation service.
     *
     * @return health status
     */
    @Operation(summary = "Health check", description = "Check the health status of the recommendation service")
    @GetMapping("/health")
    public Result<RecommendResponseVO> healthCheck() {
        RecommendResponseVO response = recommendationService.healthCheck();
        return wrapResponse(response);
    }

    /**
     * Wrap the recommendation response in a Result.
     */
    private Result<RecommendResponseVO> wrapResponse(RecommendResponseVO response) {
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return Result.success(response);
        } else {
            return Result.error(response.getCode(), response.getMessage());
        }
    }
}

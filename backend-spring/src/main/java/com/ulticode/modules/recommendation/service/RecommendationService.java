package com.ulticode.modules.recommendation.service;

import com.ulticode.modules.recommendation.dto.GetRecommendationsDTO;
import com.ulticode.modules.recommendation.dto.RecommendResponseVO;

/**
 * Service interface for problem recommendations.
 * Provides personalized problem recommendations by calling an external recommendation microservice.
 */
public interface RecommendationService {

    /**
     * Check if the recommendation service is enabled and available.
     *
     * @return true if service is available, false otherwise
     */
    boolean isAvailable();

    /**
     * Get personalized recommendations for the current user.
     *
     * @param dto the request parameters
     * @return recommendation response with recommended problems
     */
    RecommendResponseVO getRecommendations(GetRecommendationsDTO dto);

    /**
     * Get daily recommendations for the current user.
     *
     * @param limit maximum number of recommendations
     * @return recommendation response
     */
    RecommendResponseVO getDailyRecommendations(Integer limit);

    /**
     * Get similar problems to a given problem.
     *
     * @param problemId the problem ID to find similar problems for
     * @param limit     maximum number of recommendations
     * @return recommendation response with similar problems
     */
    RecommendResponseVO getSimilarProblems(Long problemId, Integer limit);

    /**
     * Get weak point recommendations based on user's submission history.
     *
     * @param limit maximum number of recommendations
     * @return recommendation response targeting weak areas
     */
    RecommendResponseVO getWeakPointRecommendations(Integer limit);

    /**
     * Get challenge recommendations (harder problems).
     *
     * @param limit maximum number of recommendations
     * @return recommendation response with challenging problems
     */
    RecommendResponseVO getChallengeRecommendations(Integer limit);

    /**
     * Health check for the recommendation service.
     *
     * @return health status
     */
    RecommendResponseVO healthCheck();
}

package com.ulticode.recommend.api;

import com.ulticode.recommend.api.dto.RecommendRequest;
import com.ulticode.recommend.api.dto.RecommendResponse;
import com.ulticode.recommend.api.dto.RecommendResult;

/**
 * Dubbo3 service interface for the recommendation system.
 * Provides problem recommendations based on various scenarios.
 */
public interface RecommendService {

    /**
     * Get problem recommendations based on the request parameters.
     *
     * @param request the recommendation request containing user ID, scenario, and filters
     * @return response containing recommendation results or error information
     */
    RecommendResponse<RecommendResult> recommend(RecommendRequest request);
}

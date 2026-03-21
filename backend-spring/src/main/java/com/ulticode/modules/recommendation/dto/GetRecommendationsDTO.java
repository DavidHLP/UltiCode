package com.ulticode.modules.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * DTO for getting personalized recommendations.
 */
@Data
public class GetRecommendationsDTO {

    /**
     * Maximum number of recommendations to return
     */
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 50, message = "Limit must not exceed 50")
    private Integer limit = 10;

    /**
     * Recommendation scenario type
     * Values: DAILY, SIMILAR, WEAK_POINT, CHALLENGE
     */
    private String scenario;

    /**
     * Problem ID for similar problem recommendations
     * Required when scenario is SIMILAR
     */
    private Long problemId;

    /**
     * Whether to include problem details
     */
    private Boolean includeDetails = false;
}

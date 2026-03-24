package com.ulticode.recommend.api.dto;

import com.ulticode.recommend.api.enums.RecommendScenario;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Request DTO for recommendation service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User identifier (required).
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Number of recommendations to return (default: 10).
     */
    @Builder.Default
    @Min(value = 1, message = "Size must be at least 1")
    private int size = 10;

    /**
     * Recommendation scenario (default: DAILY).
     */
    @NotNull(message = "Scenario is required")
    @Builder.Default
    private RecommendScenario scenario = RecommendScenario.DAILY;

    /**
     * Source problem ID for SIMILAR scenario.
     * Required when scenario is SIMILAR.
     */
    private Long sourceProblemId;

    /**
     * Target tags for filtering recommendations.
     */
    private List<String> targetTags;

    /**
     * Whether to include already solved problems (default: false).
     */
    @Builder.Default
    private boolean includeSolved = false;
}

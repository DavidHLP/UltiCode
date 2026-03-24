package com.ulticode.recommend.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the context for generating recommendations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendContext {

    private String userId;

    @Builder.Default
    private int size = 10;

    @Builder.Default
    private Scenario scenario = Scenario.DAILY;

    private Long sourceProblemId;
    private String[] targetTags;
    private String minDifficulty;
    private String maxDifficulty;

    @Builder.Default
    private boolean includeSolved = false;

    /**
     * Enumeration of recommendation scenarios.
     */
    public enum Scenario {
        /**
         * Daily practice recommendations.
         */
        DAILY,

        /**
         * Similar problem recommendations based on a source problem.
         */
        SIMILAR,

        /**
         * Recommendations focused on user's weak points.
         */
        WEAK_POINT,

        /**
         * Challenging problems for skill improvement.
         */
        CHALLENGE
    }
}

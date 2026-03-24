package com.ulticode.recommend.api.enums;

/**
 * Enumeration of recommendation scenarios.
 * Each scenario represents a different use case for problem recommendations.
 */
public enum RecommendScenario {

    /**
     * Daily practice - general recommendations based on user history and preferences.
     */
    DAILY("日常练习", "Daily practice recommendations"),

    /**
     * Similar problems - find problems similar to a specific problem.
     */
    SIMILAR("相似题目", "Similar problem recommendations"),

    /**
     * Weak point strengthening - focus on user's weak areas.
     */
    WEAK_POINT("弱点强化", "Weak point strengthening recommendations"),

    /**
     * Challenge mode - harder problems to push user limits.
     */
    CHALLENGE("挑战模式", "Challenge mode recommendations");

    private final String displayName;
    private final String description;

    RecommendScenario(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

package com.ulticode.infrastructure.redis;

import java.util.concurrent.TimeUnit;

/**
 * Cache key prefixes and TTL constants for Redis.
 * Centralizes all cache-related constants for consistent key management.
 */
public final class CacheConstants {

    private CacheConstants() {
        // Prevent instantiation
    }

    // ==================== Key Prefixes ====================

    /**
     * Refresh token cache prefix.
     * Format: refresh_token:{userId}:{tokenId}
     */
    public static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    /**
     * User cache prefix.
     * Format: user:{userId}
     */
    public static final String USER_CACHE_PREFIX = "user:";

    /**
     * Problem cache prefix.
     * Format: problem:{problemId}
     */
    public static final String PROBLEM_CACHE_PREFIX = "problem:";

    /**
     * Submission rate limit prefix.
     * Format: submission_rate:{userId}
     */
    public static final String SUBMISSION_RATE_LIMIT_PREFIX = "submission_rate:";

    /**
     * Global ranking cache key.
     */
    public static final String GLOBAL_RANKING_CACHE = "global_ranking";

    /**
     * Contest ranking cache prefix.
     * Format: contest_ranking:{contestId}
     */
    public static final String CONTEST_RANKING_PREFIX = "contest_ranking:";

    /**
     * Problem list cache prefix.
     * Format: problem_list:{listId}
     */
    public static final String PROBLEM_LIST_PREFIX = "problem_list:";

    /**
     * Email verification code prefix.
     * Format: email_verify:{email}
     */
    public static final String EMAIL_VERIFY_PREFIX = "email_verify:";

    /**
     * Password reset token prefix.
     * Format: pwd_reset:{token}
     */
    public static final String PASSWORD_RESET_PREFIX = "pwd_reset:";

    // ==================== TTL Constants ====================

    /**
     * Refresh token TTL: 7 days.
     */
    public static final long REFRESH_TOKEN_TTL = TimeUnit.DAYS.toSeconds(7);

    /**
     * User cache TTL: 30 minutes.
     */
    public static final long USER_CACHE_TTL = TimeUnit.MINUTES.toSeconds(30);

    /**
     * Problem cache TTL: 1 hour.
     */
    public static final long PROBLEM_CACHE_TTL = TimeUnit.HOURS.toSeconds(1);

    /**
     * Submission rate limit TTL: 1 minute.
     * Used to limit submission frequency.
     */
    public static final long SUBMISSION_RATE_LIMIT_TTL = TimeUnit.MINUTES.toSeconds(1);

    /**
     * Global ranking cache TTL: 5 minutes.
     */
    public static final long GLOBAL_RANKING_TTL = TimeUnit.MINUTES.toSeconds(5);

    /**
     * Contest ranking cache TTL: 30 seconds.
     * Shorter TTL for real-time contest updates.
     */
    public static final long CONTEST_RANKING_TTL = TimeUnit.SECONDS.toSeconds(30);

    /**
     * Problem list cache TTL: 10 minutes.
     */
    public static final long PROBLEM_LIST_TTL = TimeUnit.MINUTES.toSeconds(10);

    /**
     * Email verification code TTL: 5 minutes.
     */
    public static final long EMAIL_VERIFY_TTL = TimeUnit.MINUTES.toSeconds(5);

    /**
     * Password reset token TTL: 15 minutes.
     */
    public static final long PASSWORD_RESET_TTL = TimeUnit.MINUTES.toSeconds(15);

    // ==================== Rate Limit Constants ====================

    /**
     * Maximum submissions per minute per user.
     */
    public static final int MAX_SUBMISSIONS_PER_MINUTE = 10;

    /**
     * Maximum login attempts per 15 minutes.
     */
    public static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * Login rate limit TTL: 15 minutes.
     */
    public static final long LOGIN_RATE_LIMIT_TTL = TimeUnit.MINUTES.toSeconds(15);

    // ==================== Helper Methods ====================

    /**
     * Build refresh token key.
     *
     * @param userId the user ID
     * @return the cache key
     */
    public static String refreshTokenKey(Long userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }

    /**
     * Build user cache key.
     *
     * @param userId the user ID
     * @return the cache key
     */
    public static String userKey(Long userId) {
        return USER_CACHE_PREFIX + userId;
    }

    /**
     * Build problem cache key.
     *
     * @param problemId the problem ID
     * @return the cache key
     */
    public static String problemKey(Long problemId) {
        return PROBLEM_CACHE_PREFIX + problemId;
    }

    /**
     * Build submission rate limit key.
     *
     * @param userId the user ID
     * @return the cache key
     */
    public static String submissionRateLimitKey(Long userId) {
        return SUBMISSION_RATE_LIMIT_PREFIX + userId;
    }

    /**
     * Build contest ranking key.
     *
     * @param contestId the contest ID
     * @return the cache key
     */
    public static String contestRankingKey(Long contestId) {
        return CONTEST_RANKING_PREFIX + contestId;
    }

    /**
     * Build problem list key.
     *
     * @param listId the list ID
     * @return the cache key
     */
    public static String problemListKey(Long listId) {
        return PROBLEM_LIST_PREFIX + listId;
    }

    /**
     * Build email verification key.
     *
     * @param email the email address
     * @return the cache key
     */
    public static String emailVerifyKey(String email) {
        return EMAIL_VERIFY_PREFIX + email;
    }

    /**
     * Build password reset key.
     *
     * @param token the reset token
     * @return the cache key
     */
    public static String passwordResetKey(String token) {
        return PASSWORD_RESET_PREFIX + token;
    }
}

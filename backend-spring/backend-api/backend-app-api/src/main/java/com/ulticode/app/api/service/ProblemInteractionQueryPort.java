package com.ulticode.app.api.service;

/**
 * Read-side port for edge interactions associated with a Problem.
 *
 * <p>Providers preserve the existing safe-degrade behavior: favorite counts
 * use zero when unavailable, and an absent viewer reaction is represented by
 * {@code null}.
 */
public interface ProblemInteractionQueryPort {

    /**
     * Count users who favorited a Problem.
     *
     * @param problemId Problem ID; null is treated as no matching Problem
     * @return favorite count, or zero when unavailable
     */
    int countFavorites(Long problemId);

    /**
     * Resolve the latest reaction of one user on one Problem.
     *
     * @param userId viewer user ID
     * @param problemId Problem ID
     * @return reaction type, or {@code null} when absent
     */
    String findViewerReaction(String userId, Long problemId);
}

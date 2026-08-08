package com.ulticode.app.api.service;

import java.util.List;
import java.util.Map;

/**
 * Read-side port for problem-tag statistics queries owned by the App service.
 *
 * <p>Consumed by the user module's {@code DefaultUserReadProjection} to read
 * per-user tag stats (solved-problem counts grouped by tag) without importing
 * {@code ProblemTagRelationMapper} directly.
 *
 * <p>Non-throwing contract: returns an empty list when no matching rows exist.
 */
public interface ProblemTagStatsReadPort {

    /**
     * Return tag-level solved-problem statistics for a user.
     *
     * <p>The result rows carry at least: tagName, tagSlug, and count.
     * Missing or null rows are returned as an empty list.
     *
     * @param userId the user id
     * @return list of tag-stat rows, never null
     */
    List<Map<String, Object>> findTagStatsByUserId(String userId);
}

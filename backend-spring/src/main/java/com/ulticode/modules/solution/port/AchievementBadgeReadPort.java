package com.ulticode.modules.solution.port;

import java.util.List;

/**
 * Consumer-owned read seam the {@code solution} module uses to read
 * user achievements for badge display, without importing
 * {@code UserAchievementMapper} or {@code AchievementMapper} directly.
 *
 * <p>Used by {@code DefaultSolutionProjection.toVO} to populate
 * {@code SolutionVO.badges} and {@code SolutionVO.flair}. Adapter
 * lives in the {@code achievement} module.
 *
 * @author ulticode
 */
public interface AchievementBadgeReadPort {

    /**
     * Return up to {@code limit} badge names earned by the user, in
     * the order chosen by the adapter (most-recently-earned first).
     * Returns an empty list when the user has no achievements.
     */
    List<String> findBadgeNames(String userId, int limit);
}
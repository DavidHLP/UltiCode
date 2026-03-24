package com.ulticode.recommend.core.rank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.List;

/**
 * Strategy interface for ranking phase.
 *
 * <p>Ranking is the second stage of recommendation pipeline,
 * responsible for scoring and sorting candidate items from recall phase.
 */
public interface RankStrategy {

    /**
     * Rank candidate items.
     *
     * @param items candidate items from recall phase
     * @param context recommendation context
     * @param profile user profile
     * @return ranked list of items (sorted by score descending)
     */
    List<RecommendItem> rank(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    );

    /**
     * Get strategy name for logging and debugging.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Get priority (higher = applied first when multiple strategies).
     */
    default int getPriority() {
        return 0;
    }
}

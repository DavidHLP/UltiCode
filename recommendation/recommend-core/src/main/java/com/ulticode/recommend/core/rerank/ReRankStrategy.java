package com.ulticode.recommend.core.rerank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.List;

/**
 * Strategy interface for re-ranking phase.
 *
 * <p>Re-ranking is the final stage of recommendation pipeline,
 * responsible for adjusting ranking based on business rules
 * (diversity, freshness, etc.)
 */
public interface ReRankStrategy {

    /**
     * Re-rank items.
     *
     * @param items ranked items from rank phase
     * @param context recommendation context
     * @param profile user profile
     * @return re-ranked list of items
     */
    List<RecommendItem> rerank(
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

package com.ulticode.recommend.core.recall;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.List;

/**
 * Strategy interface for recall phase.
 *
 * <p>Recall is the first stage of recommendation pipeline,
 * responsible for generating candidate items from large corpus.
 */
public interface RecallStrategy {

    /**
     * Recall candidate items.
     *
     * @param context recommendation context
     * @param profile user profile
     * @return list of candidate items (unsorted)
     */
    List<RecommendItem> recall(RecommendContext context, UserProfile profile);

    /**
     * Get strategy name for logging and debugging.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Get priority (higher = more important).
     * Used for multi-strategy fusion.
     */
    default int getPriority() {
        return 0;
    }
}

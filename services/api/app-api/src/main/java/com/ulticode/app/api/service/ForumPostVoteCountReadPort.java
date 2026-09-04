package com.ulticode.app.api.service;

import java.util.Collection;
import java.util.Map;

/**
 * ADMIN-007: consumer-owned read seam the Admin service uses to query the
 * {@code edge_operations} table for forum-post vote counts, without
 * importing {@code EdgeOperationMapper} directly.
 *
 * <p>The solution vote implementation follows the same typed port shape:
 * the target-type filter is owned by each App-private adapter and never
 * threaded through this seam as a String.
 *
 * @author ulticode
 */
public interface ForumPostVoteCountReadPort {

    /**
     * Batch-count upvotes (VOTE_UP on FORUM_POST) across many posts.
     *
     * @param postIds candidate post IDs
     * @return map from post id to count; posts with no votes are absent
     *         (consumers coerce to 0). Empty input returns an empty map.
     */
    Map<String, Long> countVoteUpByTargets(Collection<String> postIds);

    /**
     * Batch-count downvotes (VOTE_DOWN on FORUM_POST) across many posts.
     *
     * @param postIds candidate post IDs
     * @return map from post id to count; posts with no votes are absent
     *         (consumers coerce to 0). Empty input returns an empty map.
     */
    Map<String, Long> countVoteDownByTargets(Collection<String> postIds);
}

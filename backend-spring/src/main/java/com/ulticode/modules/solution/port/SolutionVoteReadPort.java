package com.ulticode.modules.solution.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Consumer-owned read seam the {@code solution} module uses to query
 * the {@code edge_operations} table for vote-related data, without
 * importing {@code EdgeOperationMapper} directly.
 *
 * <p>Before this port existed, {@code DefaultSolutionProjection}
 * touched the {@code vote} module's mapper to count likes / dislikes and
 * to read the current viewer's vote state. That leaked the storage
 * shape ({@code target_id}, {@code operation_type}, {@code cnt}) into
 * the solution cluster and forced every test to mock raw SQL outputs.
 *
 * <p>Adapter lives in {@code modules.vote}. This module only sees the
 * port.
 *
 * @author ulticode
 */
public interface SolutionVoteReadPort {

    /**
     * Count likes (VOTE_UP) for a single target.
     */
    long countLikes(String targetId, String targetType);

    /**
     * Count dislikes (VOTE_DOWN) for a single target.
     */
    long countDislikes(String targetId, String targetType);

    /**
     * Batch-count likes across many targets. Returns a map from target
     * id to count; missing targets are simply absent (count 0).
     */
    Map<String, Long> countLikesByTargets(Collection<String> targetIds, String targetType);

    /**
     * Batch-count dislikes across many targets.
     */
    Map<String, Long> countDislikesByTargets(Collection<String> targetIds, String targetType);

    /**
     * Read the current viewer's vote state across many targets. Returns
     * a map from target id to {@code +1} (liked), {@code -1} (disliked),
     * {@code 0} (no vote).
     */
    Map<String, Integer> viewerVotes(String viewerId, Collection<String> targetIds, String targetType);

    /**
     * Raw form for tests and admin tools. Production callers should
     * prefer {@link #viewerVotes}.
     */
    List<Map<String, Object>> findRawByOperatorAndTargets(
            String operatorId, Collection<String> targetIds, String targetType);
}
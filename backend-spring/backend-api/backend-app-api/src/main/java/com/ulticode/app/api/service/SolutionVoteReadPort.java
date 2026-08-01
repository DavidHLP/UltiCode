package com.ulticode.app.api.service;

import java.util.Collection;
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
 * <p>The port is solution-scoped: it only reads votes whose target is a
 * solution. The {@code target_type = 'SOLUTION'} filter is owned by the
 * adapter, never threaded through the seam as a {@code String} — that
 * avoids primitive obsession at the interface and keeps the
 * {@code vote} module's enum out of the {@code solution} cluster.
 *
 * <p>Adapter lives in {@code modules.vote}. This module only sees the
 * port.
 *
 * @author ulticode
 */
public interface SolutionVoteReadPort {

    /**
     * Count likes (VOTE_UP) for a single solution.
     */
    long countLikes(String solutionId);

    /**
     * Count dislikes (VOTE_DOWN) for a single solution.
     */
    long countDislikes(String solutionId);

    /**
     * Batch-count likes across many solutions. Returns a map from solution
     * id to count; missing solutions are simply absent (count 0).
     */
    Map<String, Long> countLikesByTargets(Collection<String> solutionIds);

    /**
     * Batch-count dislikes across many solutions.
     */
    Map<String, Long> countDislikesByTargets(Collection<String> solutionIds);

    /**
     * Read the current viewer's vote state across many solutions. Returns
     * a map from solution id to {@code +1} (liked), {@code -1} (disliked);
     * a missing entry means no vote.
     */
    Map<String, Integer> viewerVotes(String viewerId, Collection<String> solutionIds);
}

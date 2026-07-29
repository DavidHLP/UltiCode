package com.ulticode.modules.problem.port;

/**
 * Outbound port for querying edge-operation interactions (favorites, reactions)
 * associated with a problem.
 *
 * <p>Extracted from {@code DefaultProblemProjection}'s direct dependency on
 * {@code EdgeOperationInspector} and {@code EdgeOperationMapper} to decouple
 * the problem module from the vote / edge-operations modules.
 *
 * @author ulticode
 */
public interface ProblemInteractionQueryPort {

    /**
     * Count the number of users who favorited the given problem.
     *
     * @param problemId the problem ID
     * @return favorite count, or 0 on error
     */
    int countFavorites(Long problemId);

    /**
     * Find the most recent reaction (LIKE / DISLIKE / FAVORITE) of the given
     * user on the given problem.
     *
     * @param userId the user ID
     * @param problemId the problem ID
     * @return the reaction type string, or {@code null} if the user has no reaction
     */
    String findViewerReaction(String userId, Long problemId);
}

package com.ulticode.modules.edgeoperations.inspector;

import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;

/**
 * Read-only inspection deep module for edge-operation interaction state.
 *
 * <p>Owns every pure-read path that asks the edge-operations subsystem
 * about the world: aggregated likes / dislikes / favorites count, the
 * current viewer's vote, and the bookmark count for a single target.
 * The interface is intentionally narrow so
 * {@link com.ulticode.modules.edgeoperations.service.EdgeOperationsService}
 * can keep its write-path contract (vote / analyze / view / favorite
 * toggle) without dragging read concerns along.
 *
 * <p>Deliberately side-effect free: every method here returns a
 * snapshot and does not mutate {@code edge_operations} or
 * {@code bookmarks} state. Write-with-side-effect paths
 * (toggle / vote / favorite) stay on {@code EdgeOperationsService}.
 *
 * <p>Test surface: a unit test for this module mocks
 * {@code VoteService} and {@code BookmarkMapper} only — no write-path
 * collaborator is needed because there is no write path on the
 * inspector seam.
 *
 * <p>Reused by:
 * <ul>
 *   <li>{@code EdgeOperationsController} read endpoints
 *       (GET {@code /edge-operations/interactions} and
 *       GET {@code /{targetType}/{targetId}})</li>
 *   <li>{@code DefaultProblemProjection#buildInteractions} which needs
 *       the real favorites count for a problem</li>
 *   <li>{@code EdgeOperationsServiceImpl#performOperation} which calls
 *       the inspector after a vote so the response carries the
 *       post-mutation favorites count without exposing the bookmark
 *       mapper to the service</li>
 * </ul>
 *
 * @see com.ulticode.modules.edgeoperations.service.EdgeOperationsService
 *      the matching write module
 * @see com.ulticode.modules.bookmark.entity.enums.BookmarkType#leafTypes()
 *      the source of truth for which target types carry a bookmark
 *      count
 */
public interface EdgeOperationInspector {

    /**
     * Get aggregated interaction stats for a target: like count, dislike
     * count, favorites count, and the current viewer's vote (1 / -1 / 0).
     *
     * <p>Works for both authenticated and anonymous callers; an anonymous
     * {@code userId} (i.e. {@code null}) yields a {@code ViewerState}
     * with {@code vote = 0} because there is no per-user row to read.
     *
     * @param userId     the user id of the caller, or {@code null} for
     *                   anonymous access
     * @param targetId   the target id (problem / solution / forum post /
     *                   etc.)
     * @param targetType the target type
     * @return populated interaction VO; never {@code null}
     */
    EdgeOperationResponseVO getInteractions(String userId, String targetId,
                                            EdgeOperationTargetType targetType);

    /**
     * Count the number of users who favorited/bookmarked a target.
     *
     * <p>Aggregates rows in the {@code bookmarks} table where
     * {@code target_type} matches the given target. The set of
     * bookmarkable target types is sourced from
     * {@link com.ulticode.modules.bookmark.entity.enums.BookmarkType#leafTypes()};
     * non-leaf types (e.g. {@code POST}, {@code COMMENT},
     * {@code PROBLEM_LIST}) return 0 because the bookmark module does
     * not store rows for them.
     *
     * <p>This is the read helper reused by the write module
     * {@code EdgeOperationsServiceImpl#handleVoteOperation} to keep
     * {@code BookmarkMapper} off the service's bean graph; the
     * inspector is the only collaborator that knows how to count
     * favorites.
     *
     * @param targetId   the target id
     * @param targetType the target type
     * @return the favorites count, or 0 for non-leaf target types
     */
    long getFavoritesCount(String targetId, EdgeOperationTargetType targetType);
}

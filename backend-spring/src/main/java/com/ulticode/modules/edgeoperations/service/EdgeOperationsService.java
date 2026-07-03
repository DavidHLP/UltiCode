package com.ulticode.modules.edgeoperations.service;

import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;

/**
 * Service interface for the write path of edge operations.
 *
 * <p>Owns every state-changing edge operation: voting (VOTE_UP /
 * VOTE_DOWN), analyzing, viewing, favoriting, and the denormalized
 * vote-count projection on the {@code solution} table. Pure-read
 * paths (interaction stats, favorites count) live on
 * {@link com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector};
 * the service injects the inspector for its own internal
 * post-mutation reads but does not expose them on its own surface.
 */
public interface EdgeOperationsService {

    /**
     * Perform an edge operation (vote, analyze, view, etc.) on a target.
     *
     * <p>For VOTE_UP and VOTE_DOWN operations:
     * <ul>
     *   <li>Delegates to {@code VoteService} with three-state toggle
     *       logic (1 / -1 / 0) and re-projects the denormalized
     *       vote counts onto the {@code solution} table when the target
     *       type is SOLUTION.</li>
     * </ul>
     *
     * <p>For other operations (ANALYZE, VIEW, LIKE, DISLIKE, FAVORITE):
     * <ul>
     *   <li>Toggle: insert if not exists, delete if exists. The
     *       response VO only exposes aggregated counts; the per-user
     *       "did this user favorite?" flag is not part of the contract
     *       (see docs/edge-operations-api-test-report-2026-06-11.md §六).</li>
     * </ul>
     *
     * @param userId the user performing the operation
     * @param dto    the operation request
     * @return the interaction stats after the operation, sourced from
     *         {@link com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector}
     *         so the response shape matches the read endpoints
     */
    EdgeOperationResponseVO performOperation(String userId, EdgeOperationDTO dto);
}

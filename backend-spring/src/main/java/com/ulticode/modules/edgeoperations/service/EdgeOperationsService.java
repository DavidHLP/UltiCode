package com.ulticode.modules.edgeoperations.service;

import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;

/**
 * Service interface for edge operations.
 * Handles operations like voting, analyzing, viewing, and retrieving interaction stats.
 */
public interface EdgeOperationsService {

    /**
     * Perform an edge operation (vote, analyze, view, etc.).
     *
     * For VOTE_UP and VOTE_DOWN operations:
     * - Delegates to VoteService with toggle logic
     *
     * For other operations (ANALYZE, VIEW, etc.):
     * - Toggle: create if not exists, delete if exists
     *
     * @param userId the user performing the operation
     * @param dto    the operation request
     * @return the interaction stats after the operation
     */
    EdgeOperationResponseVO performOperation(String userId, EdgeOperationDTO dto);

    /**
     * Get interaction stats for a target.
     *
     * @param userId     the user ID (can be null for anonymous)
     * @param targetId   the target ID
     * @param targetType the target type
     * @return the interaction stats
     */
    EdgeOperationResponseVO getInteractions(String userId, String targetId, EdgeOperationTargetType targetType);
}

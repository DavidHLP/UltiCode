package com.ulticode.modules.vote.service;

import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;

/**
 * Service interface for vote operations.
 * Handles upvoting, downvoting, and retrieving vote counts.
 */
public interface VoteService {

    /**
     * Vote on a target item.
     * Three-state voting logic:
     * - value = 1: upvote (remove existing downvote if any, add upvote)
     * - value = -1: downvote (remove existing upvote if any, add downvote)
     * - value = 0: remove vote (neutral state)
     *
     * @param userId the user performing the vote
     * @param dto    the vote request
     * @return the vote result with counts and user's vote
     */
    VoteResultVO vote(String userId, VoteDTO dto);

    /**
     * Get vote counts and user's vote for a target.
     *
     * @param userId     the user ID (can be null for anonymous)
     * @param targetId   the target ID
     * @param targetType the target type
     * @return the vote result with counts and user's vote
     */
    VoteResultVO getVoteStatus(String userId, String targetId, EdgeOperationTargetType targetType);
}

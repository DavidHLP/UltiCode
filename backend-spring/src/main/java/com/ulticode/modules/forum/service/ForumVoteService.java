package com.ulticode.modules.forum.service;

import com.ulticode.modules.vote.dto.VoteResultVO;

/**
 * Service interface for forum vote enrichment.
 * Delegates to VoteService for vote status on forum posts.
 */
public interface ForumVoteService {

    /**
     * Get vote status for a forum post.
     *
     * @param userId  the user ID (can be null for anonymous)
     * @param postId  the forum post ID
     * @return the vote result with counts and user's vote
     */
    VoteResultVO getPostVoteStatus(String userId, String postId);
}

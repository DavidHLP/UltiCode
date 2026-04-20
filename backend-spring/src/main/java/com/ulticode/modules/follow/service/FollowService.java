package com.ulticode.modules.follow.service;

import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.common.response.PageResult;

/**
 * Service interface for follow operations.
 */
public interface FollowService {

    /**
     * Follow a user (idempotent).
     *
     * @param currentUserId the current authenticated user
     * @param targetUserId the user to follow
     * @return updated follow stats
     */
    FollowStatsDTO follow(String currentUserId, String targetUserId);

    /**
     * Unfollow a user.
     *
     * @param currentUserId the current authenticated user
     * @param targetUserId the user to unfollow
     * @return updated follow stats
     */
    FollowStatsDTO unfollow(String currentUserId, String targetUserId);

    /**
     * Get paginated followers of a user.
     *
     * @param userId the user whose followers to retrieve
     * @param page the page number (1-based)
     * @param pageSize the page size
     * @return paginated follower list
     */
    PageResult<UserSummaryDTO> getFollowers(String userId, int page, int pageSize);

    /**
     * Get paginated following list of a user.
     *
     * @param userId the user whose following list to retrieve
     * @param page the page number (1-based)
     * @param pageSize the page size
     * @return paginated following list
     */
    PageResult<UserSummaryDTO> getFollowing(String userId, int page, int pageSize);

    /**
     * Get follow stats for a user.
     *
     * @param userId the user ID
     * @return follow stats
     */
    FollowStatsDTO getFollowStats(String userId);
}

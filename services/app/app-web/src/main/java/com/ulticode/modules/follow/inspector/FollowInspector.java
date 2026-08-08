package com.ulticode.modules.follow.inspector;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;

/**
 * Read-only inspection deep module for the follow graph.
 *
 * <p>Owns every pure-read path that asks the follow subsystem about the
 * world: paginated follower / following lists, aggregate follow counts,
 * and the per-viewer "is the current user following the target" check.
 * The interface is intentionally narrow so
 * {@link com.ulticode.modules.follow.service.FollowService}
 * can keep its write-path contract (follow / unfollow) without dragging
 * read concerns — pagination math, batch count enrichment, and the
 * {@code UserSummaryDTO} formatting — along.
 *
 * <p>Deliberately side-effect free: every method here returns a snapshot
 * and does not mutate {@code user_follow} state, dispatch notifications,
 * or trigger achievements. Write-with-side-effect paths
 * (follow / unfollow) stay on {@code FollowService}.
 *
 * <p>Test surface: a unit test for this module mocks
 * {@code FollowMapper} and {@code UserMapper} only — no write-path
 * collaborator (achievement trigger, notification dispatch) is needed
 * because there is no write path on the inspector seam.
 *
 * <p>Reused by:
 * <ul>
 *   <li>{@code FollowController} read endpoints
 *       (GET {@code /users/{id}/followers},
 *       GET {@code /users/{id}/following},
 *       GET {@code /users/{id}/follow/status})</li>
 *   <li>{@code FollowServiceImpl#follow} and
 *       {@code FollowServiceImpl#unfollow} which call
 *       {@link #getFollowStats(String)} after a mutation so the
 *       response carries the post-mutation counts without forcing the
 *       write module to re-implement the count read</li>
 * </ul>
 *
 * @see com.ulticode.modules.follow.service.FollowService
 *      the matching write module
 */
public interface FollowInspector {

    /**
     * Get a paginated list of a user's followers (users who follow the
     * given user), enriched with per-user follower / following counts.
     *
     * <p>Page and page size are clamped to safe bounds (page {@code >= 1},
     * page size in {@code [1, 100]}). Each {@link UserSummaryDTO} carries
     * a truncated bio (≤ 100 chars) so the listing payload stays bounded.
     *
     * @param userId   the user whose followers to retrieve
     * @param page     the page number (1-based; values {@code < 1} are
     *                 clamped to 1)
     * @param pageSize the page size (clamped to {@code [1, 100]})
     * @return paginated follower list; never {@code null}
     */
    PageResult<UserSummaryDTO> getFollowers(String userId, int page, int pageSize);

    /**
     * Get a paginated list of the users a given user is following,
     * enriched with per-user follower / following counts.
     *
     * @param userId   the user whose following list to retrieve
     * @param page     the page number (1-based; values {@code < 1} are
     *                 clamped to 1)
     * @param pageSize the page size (clamped to {@code [1, 100]})
     * @return paginated following list; never {@code null}
     */
    PageResult<UserSummaryDTO> getFollowing(String userId, int page, int pageSize);

    /**
     * Get aggregate follow counts for a user: how many followers they
     * have and how many users they are following.
     *
     * <p>This is the read helper reused by the write module
     * ({@code FollowServiceImpl#follow} / {@code FollowServiceImpl#unfollow})
     * so the response carries fresh post-mutation counts without the
     * write module having to know how counts are queried.
     *
     * @param userId the user id
     * @return populated stats; never {@code null}
     */
    FollowStatsDTO getFollowStats(String userId);

    /**
     * Check whether the current user follows a target user.
     *
     * <p>Validation mirrors the write path: querying the follow status of
     * oneself is forbidden, and a non-existent target user resolves to
     * {@code USER_NOT_FOUND} rather than a silent {@code false}.
     *
     * @param currentUserId the current user's id
     * @param targetUserId  the target user's id
     * @return {@code true} if the current user follows the target user
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@code FORBIDDEN} if the caller queries themselves, or
     *         {@code USER_NOT_FOUND} if the target user does not exist
     */
    boolean isFollowing(String currentUserId, String targetUserId);
}

package com.ulticode.modules.follow.port;

/**
 * Port that exposes the follower's / following's count for a user, the
 * minimum the consumer modules (notably {@code DefaultUserReadProjection})
 * need without dragging in the {@code FollowMapper}.
 *
 * <p>Replaces the cross-module mapper leak documented in
 * {@code /tmp/architecture-review-1783485814.html} candidate 1:
 * {@code DefaultUserReadProjection} imported {@code FollowMapper} directly
 * to compute the follower's / following's counts for the user profile
 * read. The follow module now owns the read and ships the counts.
 *
 * <p>Adapters:
 * <ul>
 *   <li>{@code FollowCountAdapter} — production, delegates to
 *       {@code FollowMapper#countByFollowingId} and
 *       {@code FollowMapper#countByFollowerId}.</li>
 * </ul>
 *
 * @author ulticode
 */
public interface FollowCountPort {

    /**
     * @param userId the user whose follower count is requested
     * @return number of users following {@code userId}, or 0 if user
     *         does not exist or has no followers
     */
    long countFollowers(String userId);

    /**
     * @param userId the user whose following count is requested
     * @return number of users {@code userId} follows, or 0 if user
     *         does not exist or follows no one
     */
    long countFollowing(String userId);
}

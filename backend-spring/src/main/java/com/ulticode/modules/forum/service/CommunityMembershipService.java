package com.ulticode.modules.forum.service;

/**
 * Community membership write surface for the forum domain.
 *
 * <p>Owns the join / leave invariants: community existence, idempotent join
 * (concurrent joins on the same (community, user) pair converge to a single
 * membership without double-counting), and leave-with-counter-consistency
 * (the member counter is only decremented when a row was actually removed).
 *
 * @author ulticode
 */
public interface CommunityMembershipService {

    /**
     * Join a community. Idempotent under concurrent calls: the
     * {@code (community_id, user_id)} unique constraint converges races to a
     * single membership.
     *
     * @param communityId the community ID
     * @param userId      the joining user ID
     */
    void joinCommunity(String communityId, String userId);

    /**
     * Leave a community. No-op (no counter drift) when the user is not a member.
     *
     * @param communityId the community ID
     * @param userId      the leaving user ID
     */
    void leaveCommunity(String communityId, String userId);
}

package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Aggregated orphan-count snapshot for the nine App child references
 * that point at the Auth-owned {@code users} table.
 *
 * <p>Orphan semantics match the legacy scanner: a child row is an
 * orphan only if the referenced parent id does not exist <em>at
 * all</em> (soft-deleted parents still physically exist and are NOT
 * orphans).
 *
 * <p>P7-RECON-CONTRACTS-001: replaces the cross-owner
 * {@code LEFT JOIN users} SQL of the legacy monolith reconciler with
 * owner-side counts (ADR-P7-OWNER-BOUNDARY-RECONCILIATION-20260802).
 *
 * @param submissions           orphaned submissions.user_id
 * @param solutions             orphaned solutions.user_id
 * @param forumPosts            orphaned forum_posts.user_id
 * @param notifications         orphaned notifications.user_id
 * @param userProfiles          orphaned user_profiles.account_id
 * @param contestParticipants   orphaned contest_participants.user_id
 * @param userAchievements      orphaned user_achievements.user_id
 * @param userFollowsByFollower orphaned user_follows.follower_id
 * @param userFollowsByFollowing orphaned user_follows.following_id
 */
public record ReconciliationOrphanCounts(
        long submissions,
        long solutions,
        long forumPosts,
        long notifications,
        long userProfiles,
        long contestParticipants,
        long userAchievements,
        long userFollowsByFollower,
        long userFollowsByFollowing) implements Serializable {

    public static final ReconciliationOrphanCounts ZERO = new ReconciliationOrphanCounts(0, 0, 0, 0, 0, 0, 0, 0, 0);
}

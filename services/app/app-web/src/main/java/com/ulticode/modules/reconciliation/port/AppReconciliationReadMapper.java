package com.ulticode.modules.reconciliation.port;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * App-side reconciliation read model.
 *
 * <p>Each orphan query joins an App-owned child table to the shared
 * {@code users} table (Q-read of the Auth owner, allowed by
 * ADR-P7-APP-DECOMPOSITION rule 3; same precedent as
 * {@code GlobalRankingMapper}). Orphan predicate matches the legacy
 * scanner exactly: child ref non-null AND parent id absent
 * (soft-deleted parents still physically exist and are NOT orphans).
 *
 * <p>P7-RECON-CONTRACTS-001: replaces the monolith's cross-owner
 * JdbcTemplate SQL; no cross-owner DB grants introduced.
 */
@Mapper
public interface AppReconciliationReadMapper {

    @Select("SELECT COUNT(*) FROM user_profiles")
    long countUserProfiles();

    @Select("SELECT COUNT(*) FROM submissions c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanSubmissions();

    @Select("SELECT COUNT(*) FROM solutions c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanSolutions();

    @Select("SELECT COUNT(*) FROM forum_posts c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanForumPosts();

    @Select("SELECT COUNT(*) FROM notifications c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanNotifications();

    @Select("SELECT COUNT(*) FROM user_profiles c LEFT JOIN users p ON c.account_id = p.id "
            + "WHERE c.account_id IS NOT NULL AND p.id IS NULL")
    long countOrphanUserProfiles();

    @Select("SELECT COUNT(*) FROM contest_participants c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanContestParticipants();

    @Select("SELECT COUNT(*) FROM user_achievements c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanUserAchievements();

    @Select("SELECT COUNT(*) FROM user_follows c LEFT JOIN users p ON c.follower_id = p.id "
            + "WHERE c.follower_id IS NOT NULL AND p.id IS NULL")
    long countOrphanUserFollowsByFollower();

    @Select("SELECT COUNT(*) FROM user_follows c LEFT JOIN users p ON c.following_id = p.id "
            + "WHERE c.following_id IS NOT NULL AND p.id IS NULL")
    long countOrphanUserFollowsByFollowing();
}

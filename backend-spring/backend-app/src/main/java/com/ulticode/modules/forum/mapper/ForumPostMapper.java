package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.forum.entity.ForumPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis-Plus mapper for ForumPost entity.
 * Extends BaseMapper for basic CRUD operations and provides custom query methods.
 *
 * NOTE: Custom @Select queries that return List<ForumPost> DO NOT reliably
 * apply JacksonTypeHandler for JSON columns (tags, media, stats, recommendation).
 * For paginated listing queries, use BaseMapper.selectPage() with QueryWrapper
 * instead — it correctly applies all type handlers via autoResultMap.
 */
@Mapper
public interface ForumPostMapper extends BaseMapper<ForumPost> {

    // =========================================================================
    // Listing queries — prefer BaseMapper.selectPage() + QueryWrapper
    // These @Select methods are retained for non-paginated lookups only.
    // =========================================================================

    @Select("SELECT * FROM forum_posts WHERE community_id = #{communityId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findByCommunityId(@Param("communityId") String communityId);

    @Select("SELECT * FROM forum_posts WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM forum_posts WHERE permalink = #{permalink} AND is_deleted = 0 LIMIT 1")
    ForumPost findByPermalink(@Param("permalink") String permalink);

    @Select("SELECT * FROM forum_posts WHERE community_id = #{communityId} AND is_pinned = 1 AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findPinnedPosts(@Param("communityId") String communityId);

    @Select("SELECT * FROM forum_posts WHERE is_flagged = 1 AND is_deleted = 0 ORDER BY flagged_at ASC")
    List<ForumPost> findFlaggedPosts();

    @Select("SELECT * FROM forum_posts WHERE community_id = #{communityId} AND flair_type = #{flairType} AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findByFlairType(
            @Param("communityId") String communityId,
            @Param("flairType") String flairType
    );

    // =========================================================================
    // Count queries (no TypeHandler concern)
    // =========================================================================

    @Select("SELECT COUNT(*) FROM forum_posts WHERE community_id = #{communityId} AND is_deleted = 0")
    long countByCommunityId(@Param("communityId") String communityId);

    @Select("SELECT COUNT(*) FROM forum_posts WHERE user_id = #{userId} AND is_deleted = 0")
    long countByUserId(@Param("userId") String userId);

    // =========================================================================
    // Update queries (no TypeHandler concern)
    // =========================================================================

    @Update("UPDATE forum_posts SET views = views + 1 WHERE id = #{postId}")
    int incrementViews(@Param("postId") String postId);

    @Update("UPDATE forum_posts SET impressions = impressions + 1 WHERE id = #{postId}")
    int incrementImpressions(@Param("postId") String postId);

    @Update("UPDATE forum_posts SET is_pinned = #{isPinned} WHERE id = #{postId}")
    int updatePinStatus(@Param("postId") String postId, @Param("isPinned") Boolean isPinned);

    @Update("UPDATE forum_posts SET is_locked = #{isLocked} WHERE id = #{postId}")
    int updateLockStatus(@Param("postId") String postId, @Param("isLocked") Boolean isLocked);

    @Update("UPDATE forum_posts SET is_flagged = 1, flagged_reason = #{flaggedReason}, flagged_at = NOW() WHERE id = #{postId}")
    int flagPost(@Param("postId") String postId, @Param("flaggedReason") String flaggedReason);

    @Update("UPDATE forum_posts SET is_flagged = 0, flagged_reason = NULL, flagged_at = NULL WHERE id = #{postId}")
    int unflagPost(@Param("postId") String postId);

    @Update("UPDATE forum_posts SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);

    @Update("UPDATE forum_posts SET is_deleted = 1, deleted_at = NOW(), deleted_by = #{deletedBy} WHERE id = #{postId}")
    int softDelete(@Param("postId") String postId, @Param("deletedBy") String deletedBy);

    @Update("UPDATE forum_posts SET vote_state = #{voteState} WHERE id = #{postId} AND is_deleted = 0")
    int updateVoteState(@Param("postId") String postId, @Param("voteState") String voteState);

    // =========================================================================
    // Search (non-paginated, TypeHandler handled in service layer)
    // =========================================================================

    @Select("SELECT * FROM forum_posts WHERE (title LIKE CONCAT('%', #{keyword}, '%') OR excerpt LIKE CONCAT('%', #{keyword}, '%')) AND is_deleted = 0 ORDER BY created_at DESC LIMIT #{limit}")
    List<ForumPost> searchPosts(@Param("keyword") String keyword, @Param("limit") int limit);
}
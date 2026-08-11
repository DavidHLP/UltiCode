package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /**
     * Admin post list query that explicitly includes logical-deleted rows.
     */
    @Select("""
            <script>
            SELECT id,
                   community_id AS communityId,
                   user_id AS userId,
                   title,
                   excerpt,
                   views,
                   is_pinned AS isPinned,
                   is_locked AS isLocked,
                   is_flagged AS isFlagged,
                   flagged_reason AS flaggedReason,
                   flagged_at AS flaggedAt,
                   is_deleted AS isDeleted,
                   deleted_at AS deletedAt,
                   created_at AS createdAt
            FROM forum_posts
            WHERE 1 = 1
            <if test="search != null and search != ''">
              AND (title LIKE CONCAT('%', #{search}, '%')
                   OR excerpt LIKE CONCAT('%', #{search}, '%'))
            </if>
            <if test="communityId != null and communityId != ''">
              AND community_id = #{communityId}
            </if>
            <if test="authorId != null and authorId != ''">
              AND user_id = #{authorId}
            </if>
            <if test="isFlagged != null">AND is_flagged = #{isFlagged}</if>
            <if test="isPinned != null">AND is_pinned = #{isPinned}</if>
            <if test="isLocked != null">AND is_locked = #{isLocked}</if>
            <if test="isDeleted != null">AND is_deleted = #{isDeleted}</if>
            ORDER BY
            <choose>
              <when test="sortBy == 'commentCount' and sortOrder == 'asc'">
                (SELECT COUNT(*) FROM forum_comments
                 WHERE post_id = forum_posts.id AND is_deleted = 0) ASC,
                created_at ASC, id ASC
              </when>
              <when test="sortBy == 'commentCount'">
                (SELECT COUNT(*) FROM forum_comments
                 WHERE post_id = forum_posts.id AND is_deleted = 0) DESC,
                created_at DESC, id DESC
              </when>
              <when test="sortBy == 'viewCount' and sortOrder == 'asc'">views ASC, id ASC</when>
              <when test="sortBy == 'viewCount'">views DESC, id DESC</when>
              <when test="sortOrder == 'asc'">created_at ASC, id ASC</when>
              <otherwise>created_at DESC, id DESC</otherwise>
            </choose>
            </script>
            """)
    List<ForumPost> selectPageIgnoreDeleted(
            Page<ForumPost> page,
            @Param("search") String search,
            @Param("communityId") String communityId,
            @Param("authorId") String authorId,
            @Param("isFlagged") Boolean isFlagged,
            @Param("isPinned") Boolean isPinned,
            @Param("isLocked") Boolean isLocked,
            @Param("isDeleted") Boolean isDeleted,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder);

    /** Admin detail lookup that includes a logically deleted post. */
    @Select("""
            SELECT id, community_id AS communityId, user_id AS userId, title, excerpt,
                   views, is_pinned AS isPinned, is_locked AS isLocked,
                   is_flagged AS isFlagged, flagged_reason AS flaggedReason,
                   flagged_at AS flaggedAt, is_deleted AS isDeleted,
                   deleted_at AS deletedAt, created_at AS createdAt
            FROM forum_posts
            WHERE id = #{id}
            """)
    ForumPost selectByIdIgnoreDeleted(@Param("id") String id);

    /** Owner-side delete lookup that locks the row and includes logical-deleted records. */
    @Select("SELECT * FROM forum_posts WHERE id = #{id} FOR UPDATE")
    ForumPost selectByIdForUpdateIgnoreDeleted(@Param("id") String id);

    // =========================================================================
    // Update queries (no TypeHandler concern)
    // =========================================================================

    @Update("UPDATE forum_posts SET views = views + 1 WHERE id = #{postId}")
    int incrementViews(@Param("postId") String postId);

    @Update("UPDATE forum_posts SET impressions = impressions + 1 WHERE id = #{postId}")
    int incrementImpressions(@Param("postId") String postId);

    @Update("UPDATE forum_posts SET is_pinned = #{isPinned} "
            + "WHERE id = #{postId} AND is_deleted = 0")
    int updatePinStatus(@Param("postId") String postId, @Param("isPinned") Boolean isPinned);

    @Update("UPDATE forum_posts SET is_locked = #{isLocked} "
            + "WHERE id = #{postId} AND is_deleted = 0")
    int updateLockStatus(@Param("postId") String postId, @Param("isLocked") Boolean isLocked);

    @Update("UPDATE forum_posts SET is_flagged = #{isFlagged}, "
            + "flagged_reason = #{reason}, flagged_at = #{flaggedAt} "
            + "WHERE id = #{id} AND is_deleted = 0")
    int updateFlagStatusAt(@Param("id") String id,
                           @Param("isFlagged") boolean isFlagged,
                           @Param("reason") String reason,
                           @Param("flaggedAt") java.time.LocalDateTime flaggedAt);

    @Update("UPDATE forum_posts SET is_flagged = 1, flagged_reason = #{flaggedReason}, flagged_at = NOW() WHERE id = #{postId}")
    int flagPost(@Param("postId") String postId, @Param("flaggedReason") String flaggedReason);

    @Update("UPDATE forum_posts SET is_flagged = 0, flagged_reason = NULL, flagged_at = NULL WHERE id = #{postId}")
    int unflagPost(@Param("postId") String postId);

    @Update("UPDATE forum_posts SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);

    @Update("UPDATE forum_posts SET is_deleted = 1, deleted_at = NOW(), deleted_by = #{deletedBy} WHERE id = #{postId} AND is_deleted = 0")
    int softDelete(@Param("postId") String postId, @Param("deletedBy") String deletedBy);

    @Update("UPDATE forum_posts SET vote_state = #{voteState} WHERE id = #{postId} AND is_deleted = 0")
    int updateVoteState(@Param("postId") String postId, @Param("voteState") String voteState);

    // =========================================================================
    // Search (non-paginated, TypeHandler handled in service layer)
    // =========================================================================

    @Select("SELECT * FROM forum_posts WHERE (title LIKE CONCAT('%', #{keyword}, '%') OR excerpt LIKE CONCAT('%', #{keyword}, '%')) AND is_deleted = 0 ORDER BY created_at DESC LIMIT #{limit}")
    List<ForumPost> searchPosts(@Param("keyword") String keyword, @Param("limit") int limit);
}
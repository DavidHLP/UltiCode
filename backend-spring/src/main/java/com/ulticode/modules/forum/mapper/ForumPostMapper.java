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
 */
@Mapper
public interface ForumPostMapper extends BaseMapper<ForumPost> {

    /**
     * Find posts by community ID.
     *
     * @param communityId the community ID
     * @return list of posts ordered by creation time (newest first)
     */
    @Select("SELECT * FROM forum_posts WHERE community_id = #{communityId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findByCommunityId(@Param("communityId") String communityId);

    /**
     * Find posts by user ID.
     *
     * @param userId the user ID
     * @return list of posts ordered by creation time (newest first)
     */
    @Select("SELECT * FROM forum_posts WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findByUserId(@Param("userId") String userId);

    /**
     * Find post by permalink.
     *
     * @param permalink the unique permalink
     * @return the post if found
     */
    @Select("SELECT * FROM forum_posts WHERE permalink = #{permalink} AND is_deleted = 0 LIMIT 1")
    ForumPost findByPermalink(@Param("permalink") String permalink);

    /**
     * Find pinned posts in a community.
     *
     * @param communityId the community ID
     * @return list of pinned posts
     */
    @Select("SELECT * FROM forum_posts WHERE community_id = #{communityId} AND is_pinned = 1 AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findPinnedPosts(@Param("communityId") String communityId);

    /**
     * Find flagged posts for moderation.
     *
     * @return list of flagged posts
     */
    @Select("SELECT * FROM forum_posts WHERE is_flagged = 1 AND is_deleted = 0 ORDER BY flagged_at ASC")
    List<ForumPost> findFlaggedPosts();

    /**
     * Find posts by flair type.
     *
     * @param communityId the community ID
     * @param flairType   the flair type
     * @return list of posts with the given flair type
     */
    @Select("SELECT * FROM forum_posts WHERE community_id = #{communityId} AND flair_type = #{flairType} AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumPost> findByFlairType(
            @Param("communityId") String communityId,
            @Param("flairType") String flairType
    );

    /**
     * Count posts by community ID.
     *
     * @param communityId the community ID
     * @return count of posts
     */
    @Select("SELECT COUNT(*) FROM forum_posts WHERE community_id = #{communityId} AND is_deleted = 0")
    long countByCommunityId(@Param("communityId") String communityId);

    /**
     * Count posts by user ID.
     *
     * @param userId the user ID
     * @return count of posts
     */
    @Select("SELECT COUNT(*) FROM forum_posts WHERE user_id = #{userId} AND is_deleted = 0")
    long countByUserId(@Param("userId") String userId);

    /**
     * Increment view count.
     *
     * @param postId the post ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_posts SET views = views + 1 WHERE id = #{postId}")
    int incrementViews(@Param("postId") String postId);

    /**
     * Increment impressions count.
     *
     * @param postId the post ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_posts SET impressions = impressions + 1 WHERE id = #{postId}")
    int incrementImpressions(@Param("postId") String postId);

    /**
     * Toggle pin status.
     *
     * @param postId   the post ID
     * @param isPinned the new pin status
     * @return number of rows affected
     */
    @Update("UPDATE forum_posts SET is_pinned = #{isPinned} WHERE id = #{postId}")
    int updatePinStatus(@Param("postId") String postId, @Param("isPinned") Boolean isPinned);

    /**
     * Toggle lock status.
     *
     * @param postId   the post ID
     * @param isLocked the new lock status
     * @return number of rows affected
     */
    @Update("UPDATE forum_posts SET is_locked = #{isLocked} WHERE id = #{postId}")
    int updateLockStatus(@Param("postId") String postId, @Param("isLocked") Boolean isLocked);

    /**
     * Flag a post.
     *
     * @param postId        the post ID
     * @param flaggedReason the reason for flagging
     * @return number of rows affected
     */
    @Update("UPDATE forum_posts SET is_flagged = 1, flagged_reason = #{flaggedReason}, flagged_at = NOW() WHERE id = #{postId}")
    int flagPost(@Param("postId") String postId, @Param("flaggedReason") String flaggedReason);

    /**
     * Unflag a post.
     *
     * @param postId the post ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_posts SET is_flagged = 0, flagged_reason = NULL, flagged_at = NULL WHERE id = #{postId}")
    int unflagPost(@Param("postId") String postId);

    /**
     * Soft delete a post.
     *
     * @param postId    the post ID
     * @param deletedBy the user ID who deleted the post
     * @return number of rows affected
     */
    @Update("UPDATE forum_posts SET is_deleted = 1, deleted_at = NOW(), deleted_by = #{deletedBy} WHERE id = #{postId}")
    int softDelete(@Param("postId") String postId, @Param("deletedBy") String deletedBy);

    /**
     * Find recent posts across all communities.
     *
     * @param limit maximum number of posts to return
     * @return list of recent posts
     */
    @Select("SELECT * FROM forum_posts WHERE is_deleted = 0 ORDER BY created_at DESC LIMIT #{limit}")
    List<ForumPost> findRecentPosts(@Param("limit") int limit);

    /**
     * Find posts by title or excerpt (search).
     *
     * @param keyword the search keyword
     * @param limit   maximum number of posts to return
     * @return list of matching posts
     */
    @Select("SELECT * FROM forum_posts WHERE (title LIKE CONCAT('%', #{keyword}, '%') OR excerpt LIKE CONCAT('%', #{keyword}, '%')) AND is_deleted = 0 ORDER BY created_at DESC LIMIT #{limit}")
    List<ForumPost> searchPosts(@Param("keyword") String keyword, @Param("limit") int limit);
}

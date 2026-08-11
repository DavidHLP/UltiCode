package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.forum.entity.ForumComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * MyBatis-Plus mapper for ForumComment entity.
 * Extends BaseMapper for basic CRUD operations and provides custom query methods.
 */
@Mapper
public interface ForumCommentMapper extends BaseMapper<ForumComment> {

    /**
     * Find comments by post ID.
     *
     * @param postId the post ID
     * @return list of comments ordered by creation time
     */
    @Select("SELECT * FROM forum_comments WHERE post_id = #{postId} AND is_deleted = 0 ORDER BY created_at ASC")
    List<ForumComment> findByPostId(@Param("postId") String postId);

    /**
     * Find comments by author ID.
     *
     * @param authorId the author ID
     * @return list of comments ordered by creation time (newest first)
     */
    @Select("SELECT * FROM forum_comments WHERE author_id = #{authorId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<ForumComment> findByAuthorId(@Param("authorId") String authorId);

    /**
     * Find replies to a comment (children).
     *
     * @param parentId the parent comment ID
     * @return list of replies ordered by creation time
     */
    @Select("SELECT * FROM forum_comments WHERE parent_id = #{parentId} AND is_deleted = 0 ORDER BY created_at ASC")
    List<ForumComment> findReplies(@Param("parentId") String parentId);

    /**
     * Find top-level comments for a post (no parent).
     *
     * @param postId the post ID
     * @return list of top-level comments
     */
    @Select("SELECT * FROM forum_comments WHERE post_id = #{postId} AND parent_id IS NULL AND is_deleted = 0 ORDER BY created_at ASC")
    List<ForumComment> findTopLevelComments(@Param("postId") String postId);

    /**
     * Find pinned comments for a post.
     *
     * @param postId the post ID
     * @return list of pinned comments
     */
    @Select("SELECT * FROM forum_comments WHERE post_id = #{postId} AND is_pinned = 1 AND is_deleted = 0 ORDER BY created_at ASC")
    List<ForumComment> findPinnedComments(@Param("postId") String postId);

    /**
     * Find flagged comments for moderation.
     *
     * @return list of flagged comments
     */
    @Select("SELECT * FROM forum_comments WHERE is_flagged = 1 AND is_deleted = 0 ORDER BY flagged_at ASC")
    List<ForumComment> findFlaggedComments();

    /**
     * Count comments by post ID.
     *
     * @param postId the post ID
     * @return count of comments
     */
    @Select("SELECT COUNT(*) FROM forum_comments WHERE post_id = #{postId} AND is_deleted = 0")
    long countByPostId(@Param("postId") String postId);

    /**
     * Count comments by multiple post IDs.
     *
     * @param postIds the post IDs
     * @return list of maps with "post_id" and "cnt" keys
     */
    @Select("<script>SELECT post_id, COUNT(*) as cnt FROM forum_comments WHERE post_id IN " +
            "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND is_deleted = 0 GROUP BY post_id</script>")
    List<Map<String, Object>> countByPostIds(@Param("postIds") List<String> postIds);

    /**
     * Count comments by author ID.
     *
     * @param authorId the author ID
     * @return count of comments
     */
    @Select("SELECT COUNT(*) FROM forum_comments WHERE author_id = #{authorId} AND is_deleted = 0")
    long countByAuthorId(@Param("authorId") String authorId);

    /**
     * Count replies to a comment.
     *
     * @param parentId the parent comment ID
     * @return count of replies
     */
    @Select("SELECT COUNT(*) FROM forum_comments WHERE parent_id = #{parentId} AND is_deleted = 0")
    long countReplies(@Param("parentId") String parentId);

    /**
     * Toggle pin status.
     *
     * @param commentId the comment ID
     * @param isPinned  the new pin status
     * @return number of rows affected
     */
    @Update("UPDATE forum_comments SET is_pinned = #{isPinned} WHERE id = #{commentId}")
    int updatePinStatus(@Param("commentId") String commentId, @Param("isPinned") Boolean isPinned);

    /**
     * Toggle lock status.
     *
     * @param commentId the comment ID
     * @param isLocked  the new lock status
     * @return number of rows affected
     */
    @Update("UPDATE forum_comments SET is_locked = #{isLocked} WHERE id = #{commentId}")
    int updateLockStatus(@Param("commentId") String commentId, @Param("isLocked") Boolean isLocked);

    /**
     * Flag a comment.
     *
     * @param commentId     the comment ID
     * @param flaggedReason the reason for flagging
     * @return number of rows affected
     */
    @Update("UPDATE forum_comments SET is_flagged = 1, flagged_reason = #{flaggedReason}, flagged_at = NOW() WHERE id = #{commentId}")
    int flagComment(@Param("commentId") String commentId, @Param("flaggedReason") String flaggedReason);

    /**
     * Unflag a comment.
     *
     * @param commentId the comment ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_comments SET is_flagged = 0, flagged_reason = NULL, flagged_at = NULL WHERE id = #{commentId}")
    int unflagComment(@Param("commentId") String commentId);

    @Update("UPDATE forum_comments SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id} AND is_deleted = 0")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);

    /**
     * Soft delete a comment.
     *
     * @param commentId the comment ID
     * @param deletedBy the user ID who deleted the comment
     * @return number of rows affected
     */
    @Update("UPDATE forum_comments SET is_deleted = 1, deleted_at = NOW(), deleted_by = #{deletedBy} WHERE id = #{commentId} AND is_deleted = 0")
    int softDelete(@Param("commentId") String commentId, @Param("deletedBy") String deletedBy);

    /**
     * Select comment by ID ignoring logical delete (for admin queries).
     */
    @Select("SELECT * FROM forum_comments WHERE id = #{id}")
    ForumComment selectByIdIgnoreDeleted(@Param("id") String id);

    /**
     * Admin paginated query ignoring logical delete. Supports dynamic filtering.
     */
    @Select("""
            <script>
            SELECT * FROM forum_comments
            WHERE 1=1
            <if test="isFlagged != null">AND is_flagged = #{isFlagged}</if>
            <if test="isDeleted != null">AND is_deleted = #{isDeleted}</if>
            <if test="search != null and search != ''">AND body LIKE CONCAT('%', #{search}, '%')</if>
            <if test="parentEntityId != null and parentEntityId != ''">AND post_id = #{parentEntityId}</if>
            ORDER BY
            <choose>
                <when test="sortBy == 'updatedAt'">edited_at</when>
                <otherwise>created_at</otherwise>
            </choose>
            <choose>
                <when test="sortOrder == 'asc'">ASC</when>
                <otherwise>DESC</otherwise>
            </choose>
            </script>
            """)
    List<ForumComment> selectPageIgnoreDeleted(Page<ForumComment> page,
                                                @Param("isFlagged") Boolean isFlagged,
                                                @Param("isDeleted") Boolean isDeleted,
                                                @Param("search") String search,
                                                @Param("parentEntityId") String parentEntityId,
                                                @Param("sortBy") String sortBy,
                                                @Param("sortOrder") String sortOrder);

    /**
     * Update edited timestamp.
     *
     * @param commentId the comment ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_comments SET edited_at = NOW() WHERE id = #{commentId}")
    int markAsEdited(@Param("commentId") String commentId);
}

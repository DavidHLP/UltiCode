package com.ulticode.modules.forum.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.*;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;

import java.util.List;
import java.util.Map;

/**
 * Service interface for forum post operations.
 */
public interface ForumPostService {

    /**
     * Find all posts (non-paginated, returns first MAX_RECENT_POSTS items).
     *
     * @param userId optional user ID for personalization
     * @return list of posts
     */
    List<ForumPostVO> findAllPosts(String userId);

    /**
     * Find all posts with pagination.
     *
     * @param userId   optional user ID for personalization
     * @param page     page number (1-based)
     * @param pageSize items per page
     * @return paginated result
     */
    PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize);

    /**
     * Find a post by ID.
     *
     * @param id     the post ID
     * @param userId optional user ID for personalization
     * @return the post
     */
    ForumPostVO findPostById(String id, String userId);

    /**
     * Find posts by current user (non-paginated).
     *
     * @param userId the user ID
     * @return list of user's posts
     */
    List<ForumPostVO> findMyPosts(String userId);

    /**
     * Find posts by current user with pagination.
     *
     * @param userId   the user ID
     * @param page     page number (1-based)
     * @param pageSize items per page
     * @return paginated result
     */
    PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize);

    /**
     * Create a new post.
     *
     * @param dto    the create post DTO
     * @param userId the author user ID
     * @return the created post
     */
    ForumPostVO createPost(CreatePostDTO dto, String userId);

    /**
     * Update an existing post.
     *
     * @param id     the post ID
     * @param dto    the update post DTO
     * @param userId the user ID making the update
     * @return the updated post
     */
    ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId);

    /**
     * Delete a post (soft delete).
     *
     * @param id     the post ID
     * @param userId the user ID making the delete
     */
    void deletePost(String id, String userId);

    /**
     * Get post thread (post + raw comment list).
     *
     * @param postId the post ID
     * @param userId optional user ID for personalization
     * @return the post thread (post + comments, no tree building)
     */
    ForumPostThreadVO getPostThread(String postId, String userId);

    /**
     * Record a share action for a post.
     *
     * @param postId the post ID
     */
    void recordShare(String postId);

    /**
     * Record a view action for a post.
     *
     * @param postId the post ID
     */
    void recordView(String postId);

    /**
     * Count posts by community ID (used by facade for pagination).
     */
    long countByCommunityId(String communityId);

    /**
     * Find posts by community ID (used by facade for pagination).
     */
    List<ForumPost> findByCommunityId(String communityId, int limit, int offset);

    /**
     * Convert ForumPost entity to ForumPostVO (used by facade for community listing).
     */
    ForumPostVO convertToPostVO(ForumPost post, String userId, User author);

    /**
     * Batch load authors for posts (used by facade).
     */
    Map<String, User> batchLoadAuthors(List<ForumPost> posts);

    /** Convert ForumCommunity entity to ForumCommunityVO (used by facade). */
    ForumCommunityVO toCommunityVO(ForumCommunity community);

    /**
     * Convert ForumTag entity to ForumTagVO (used by facade).
     */
    ForumTagVO toTagVO(ForumTag tag);

}

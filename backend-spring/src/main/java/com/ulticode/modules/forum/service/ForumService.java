package com.ulticode.modules.forum.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.*;
import com.ulticode.modules.forum.mapper.*;

import java.util.List;

/**
 * Forum Service Interface.
 * Provides methods for managing forum posts, comments, communities, and tags.
 */
public interface ForumService {

    // =========================================================================
    // POST OPERATIONS
    // =========================================================================

    /**
     * Find all posts with optional user context.
     *
     * @param userId optional user ID for personalization
     * @return list of all posts
     */
    List<ForumPostVO> findAllPosts(String userId);

    /**
     * Find a post by ID.
     *
     * @param id     the post ID
     * @param userId optional user ID for personalization
     * @return the post
     */
    ForumPostVO findPostById(String id, String userId);

    /**
     * Find posts by current user.
     *
     * @param userId the user ID
     * @return list of user's posts
     */
    List<ForumPostVO> findMyPosts(String userId);

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
     * Delete a post.
     *
     * @param id     the post ID
     * @param userId the user ID making the delete
     */
    void deletePost(String id, String userId);

    /**
     * Get post thread (post with comments).
     *
     * @param postId the post ID
     * @param userId optional user ID for personalization
     * @return the post with comments
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

    // =========================================================================
    // COMMENT OPERATIONS
    // =========================================================================

    /**
     * Create a comment.
     *
     * @param postId    the post ID
     * @param dto       the create comment DTO
     * @param userId    the author user ID
     * @return the created comment
     */
    ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId);

    /**
     * Update a comment.
     *
     * @param id     the comment ID
     * @param dto    the update comment DTO
     * @param userId the user ID making the update
     * @return the updated comment
     */
    ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId);

    /**
     * Delete a comment.
     *
     * @param id     the comment ID
     * @param userId the user ID making the delete
     */
    void deleteComment(String id, String userId);

    // =========================================================================
    // COMMUNITY OPERATIONS
    // =========================================================================

    /**
     * Find all communities.
     *
     * @param featuredOnly whether to filter for featured communities only
     * @return list of communities
     */
    List<ForumCommunityVO> findAllCommunities(boolean featuredOnly);

    /**
     * Find a community by slug or ID.
     *
     * @param slugOrId the community slug or ID
     * @return the community detail
     */
    ForumCommunityDetailVO findCommunityBySlugOrId(String slugOrId);

    /**
     * Find posts by community slug.
     *
     * @param slug   the community slug
     * @param sortBy sort option (hot, new, top)
     * @param userId optional user ID for personalization
     * @return list of posts
     */
    List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId);

    /**
     * Join a community.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     */
    void joinCommunity(String communityId, String userId);

    /**
     * Leave a community.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     */
    void leaveCommunity(String communityId, String userId);

    // =========================================================================
    // TAG OPERATIONS
    // =========================================================================

    /**
     * Find all tags.
     *
     * @return list of tags
     */
    List<ForumTagVO> findAllTags();
}

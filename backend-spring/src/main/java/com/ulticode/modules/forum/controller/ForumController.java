package com.ulticode.modules.forum.controller;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.service.ForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for forum-related operations.
 */
@Tag(name = "Forum", description = "Forum management endpoints")
@RestController
@RequestMapping("/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;

    // =========================================================================
    // POST OPERATIONS (Public)
    // =========================================================================

    /**
     * Get all posts.
     * Public endpoint - accessible without authentication.
     *
     * @return list of all posts
     */
    @Operation(summary = "Get all posts", description = "Get all forum posts")
    @GetMapping("/posts")
    public Result<List<ForumPostVO>> getAllPosts() {
        String userId = SecurityUtil.getCurrentUserId();
        List<ForumPostVO> posts = forumService.findAllPosts(userId);
        return Result.success(posts);
    }

    /**
     * Get post by ID.
     * Public endpoint - accessible without authentication.
     *
     * @param id the post ID
     * @return the post details
     */
    @Operation(summary = "Get post by ID", description = "Get a specific post by its ID")
    @GetMapping("/posts/{id}")
    public Result<ForumPostVO> getPostById(
            @Parameter(description = "Post ID")
            @PathVariable String id) {

        String userId = SecurityUtil.getCurrentUserId();
        ForumPostVO post = forumService.findPostById(id, userId);
        return Result.success(post);
    }

    /**
     * Get current user's posts.
     * Requires authentication.
     *
     * @return list of user's posts
     */
    @Operation(summary = "Get my posts", description = "Get the current user's posts")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/me/posts")
    public Result<List<ForumPostVO>> getMyPosts() {
        String userId = getCurrentUserIdOrThrow();
        List<ForumPostVO> posts = forumService.findMyPosts(userId);
        return Result.success(posts);
    }

    /**
     * Create a new post.
     * Requires authentication.
     *
     * @param dto the create post DTO
     * @return the created post
     */
    @Operation(summary = "Create post", description = "Create a new forum post")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/posts")
    public Result<ForumPostVO> createPost(
            @Valid @RequestBody CreatePostDTO dto) {

        String userId = getCurrentUserIdOrThrow();
        ForumPostVO post = forumService.createPost(dto, userId);
        return Result.success(post);
    }

    /**
     * Update an existing post.
     * Requires authentication.
     *
     * @param id  the post ID
     * @param dto the update post DTO
     * @return the updated post
     */
    @Operation(summary = "Update post", description = "Update an existing post")
    @SecurityRequirement(name = "Bearer")
    @PatchMapping("/posts/{id}")
    public Result<ForumPostVO> updatePost(
            @Parameter(description = "Post ID")
            @PathVariable String id,
            @Valid @RequestBody UpdatePostDTO dto) {

        String userId = getCurrentUserIdOrThrow();
        ForumPostVO post = forumService.updatePost(id, dto, userId);
        return Result.success(post);
    }

    /**
     * Delete a post.
     * Requires authentication.
     *
     * @param id the post ID
     * @return success result
     */
    @Operation(summary = "Delete post", description = "Delete a post")
    @SecurityRequirement(name = "Bearer")
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(
            @Parameter(description = "Post ID")
            @PathVariable String id) {

        String userId = getCurrentUserIdOrThrow();
        forumService.deletePost(id, userId);
        return Result.success();
    }

    /**
     * Get post thread (post with comments).
     * Public endpoint - accessible without authentication.
     *
     * @param id the post ID
     * @return the post with comments
     */
    @Operation(summary = "Get post thread", description = "Get a post with its comment thread")
    @GetMapping("/posts/{id}/thread")
    public Result<ForumPostThreadVO> getPostThread(
            @Parameter(description = "Post ID")
            @PathVariable String id) {

        String userId = SecurityUtil.getCurrentUserId();
        ForumPostThreadVO thread = forumService.getPostThread(id, userId);
        return Result.success(thread);
    }

    /**
     * Record a share action for a post.
     * Public endpoint - accessible without authentication.
     *
     * @param id the post ID
     * @return success result
     */
    @Operation(summary = "Record share", description = "Record a share action for a post")
    @PostMapping("/posts/{id}/share")
    public Result<Void> recordShare(
            @Parameter(description = "Post ID")
            @PathVariable String id) {

        forumService.recordShare(id);
        return Result.success();
    }

    /**
     * Record a view action for a post.
     * Public endpoint - accessible without authentication.
     *
     * @param id the post ID
     * @return success result
     */
    @Operation(summary = "Record view", description = "Record a view action for a post")
    @PostMapping("/posts/{id}/view")
    public Result<Void> recordView(
            @Parameter(description = "Post ID")
            @PathVariable String id) {

        forumService.recordView(id);
        return Result.success();
    }

    // =========================================================================
    // COMMENT OPERATIONS (Authenticated)
    // =========================================================================

    /**
     * Create a comment on a post.
     * Requires authentication.
     *
     * @param id  the post ID
     * @param dto the create comment DTO
     * @return the created comment
     */
    @Operation(summary = "Create comment", description = "Create a comment on a post")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/posts/{id}/comments")
    public Result<ForumCommentVO> createComment(
            @Parameter(description = "Post ID")
            @PathVariable String id,
            @Valid @RequestBody CreateCommentDTO dto) {

        String userId = getCurrentUserIdOrThrow();
        ForumCommentVO comment = forumService.createComment(id, dto, userId);
        return Result.success(comment);
    }

    /**
     * Update a comment.
     * Requires authentication.
     *
     * @param id  the comment ID
     * @param dto the update comment DTO
     * @return the updated comment
     */
    @Operation(summary = "Update comment", description = "Update an existing comment")
    @SecurityRequirement(name = "Bearer")
    @PatchMapping("/comments/{id}")
    public Result<ForumCommentVO> updateComment(
            @Parameter(description = "Comment ID")
            @PathVariable String id,
            @Valid @RequestBody UpdateCommentDTO dto) {

        String userId = getCurrentUserIdOrThrow();
        ForumCommentVO comment = forumService.updateComment(id, dto, userId);
        return Result.success(comment);
    }

    /**
     * Delete a comment.
     * Requires authentication.
     *
     * @param id the comment ID
     * @return success result
     */
    @Operation(summary = "Delete comment", description = "Delete a comment")
    @SecurityRequirement(name = "Bearer")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(
            @Parameter(description = "Comment ID")
            @PathVariable String id) {

        String userId = getCurrentUserIdOrThrow();
        forumService.deleteComment(id, userId);
        return Result.success();
    }

    // =========================================================================
    // COMMUNITY OPERATIONS (Public)
    // =========================================================================

    /**
     * Get all communities.
     * Public endpoint - accessible without authentication.
     *
     * @param featured filter for featured communities only
     * @return list of communities
     */
    @Operation(summary = "Get all communities", description = "Get all forum communities")
    @GetMapping("/communities")
    public Result<List<ForumCommunityVO>> getAllCommunities(
            @Parameter(description = "Filter for featured communities only")
            @RequestParam(required = false, defaultValue = "false") Boolean featured) {

        List<ForumCommunityVO> communities = forumService.findAllCommunities(featured);
        return Result.success(communities);
    }

    /**
     * Get community by slug or ID.
     * Public endpoint - accessible without authentication.
     *
     * @param slugOrId the community slug or ID
     * @return the community details
     */
    @Operation(summary = "Get community", description = "Get a community by slug or ID")
    @GetMapping("/communities/{slugOrId}")
    public Result<ForumCommunityDetailVO> getCommunity(
            @Parameter(description = "Community slug or ID")
            @PathVariable String slugOrId) {

        ForumCommunityDetailVO community = forumService.findCommunityBySlugOrId(slugOrId);
        return Result.success(community);
    }

    /**
     * Get posts by community.
     * Public endpoint - accessible without authentication.
     *
     * @param slug   the community slug
     * @param sortBy sort option (hot, new, top)
     * @return list of posts
     */
    @Operation(summary = "Get community posts", description = "Get posts for a specific community")
    @GetMapping("/communities/{slug}/posts")
    public Result<List<ForumPostVO>> getCommunityPosts(
            @Parameter(description = "Community slug")
            @PathVariable String slug,
            @Parameter(description = "Sort by (hot, new, top)")
            @RequestParam(required = false, defaultValue = "new") String sortBy) {

        String userId = SecurityUtil.getCurrentUserId();
        List<ForumPostVO> posts = forumService.findPostsByCommunity(slug, sortBy, userId);
        return Result.success(posts);
    }

    /**
     * Join a community.
     * Requires authentication.
     *
     * @param id the community ID
     * @return success result
     */
    @Operation(summary = "Join community", description = "Join a community")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/communities/{id}/join")
    public Result<Void> joinCommunity(
            @Parameter(description = "Community ID")
            @PathVariable String id) {

        String userId = getCurrentUserIdOrThrow();
        forumService.joinCommunity(id, userId);
        return Result.success();
    }

    /**
     * Leave a community.
     * Requires authentication.
     *
     * @param id the community ID
     * @return success result
     */
    @Operation(summary = "Leave community", description = "Leave a community")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/communities/{id}/leave")
    public Result<Void> leaveCommunity(
            @Parameter(description = "Community ID")
            @PathVariable String id) {

        String userId = getCurrentUserIdOrThrow();
        forumService.leaveCommunity(id, userId);
        return Result.success();
    }

    // =========================================================================
    // TAG OPERATIONS (Public)
    // =========================================================================

    /**
     * Get all tags.
     * Public endpoint - accessible without authentication.
     *
     * @return list of tags
     */
    @Operation(summary = "Get all tags", description = "Get all forum tags")
    @GetMapping("/tags")
    public Result<List<ForumTagVO>> getAllTags() {
        List<ForumTagVO> tags = forumService.findAllTags();
        return Result.success(tags);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Get the current authenticated user's ID or throw an exception.
     *
     * @return the user ID
     * @throws BusinessException if not authenticated
     */
    private String getCurrentUserIdOrThrow() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}

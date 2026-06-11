package com.ulticode.modules.forum.controller;

import com.ulticode.common.annotation.RateLimit;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Forum", description = "Forum management endpoints")
@RestController
@RequestMapping("/forum")
@RequiredArgsConstructor
@Validated
public class ForumController {

    private final ForumService forumService;

    @Operation(summary = "Get all posts", description = "Get all forum posts with sorting and pagination")
    @GetMapping("/posts")
    public Result<PageResult<ForumPostVO>> getAllPosts(
            @Parameter(description = "Sort by (hot, new, top)")
            @RequestParam(required = false, defaultValue = "new") String sortBy,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1")
            @Min(value = 1, message = "page must be at least 1")
            @Max(value = 1000, message = "page cannot exceed 1000")
            Integer page,
            @Parameter(description = "Items per page")
            @RequestParam(required = false, defaultValue = "20")
            @Min(value = 1, message = "pageSize must be at least 1")
            @Max(value = 50, message = "pageSize cannot exceed 50")
            Integer pageSize) {
        String userId = SecurityUtil.getCurrentUserId();
        PageResult<ForumPostVO> result = forumService.findAllPosts(userId, sortBy, page, pageSize);
        return Result.success(result);
    }

    @Operation(summary = "Get post by ID", description = "Get a specific post by its ID")
    @GetMapping("/posts/{id}")
    public Result<ForumPostVO> getPostById(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        ForumPostVO post = forumService.findPostById(id, userId);
        return Result.success(post);
    }

    @Operation(summary = "Get my posts", description = "Get the current user's posts")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/me/posts")
    public Result<PageResult<ForumPostVO>> getMyPosts(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1")
            @Min(value = 1, message = "page must be at least 1")
            @Max(value = 1000, message = "page cannot exceed 1000")
            Integer page,
            @Parameter(description = "Items per page")
            @RequestParam(required = false, defaultValue = "20")
            @Min(value = 1, message = "pageSize must be at least 1")
            @Max(value = 50, message = "pageSize cannot exceed 50")
            Integer pageSize) {
        String userId = getCurrentUserIdOrThrow();
        PageResult<ForumPostVO> result = forumService.findMyPosts(userId, page, pageSize);
        return Result.success(result);
    }

    @Operation(summary = "Create post", description = "Create a new forum post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:create-post", limit = 20, period = 60)
    @PostMapping("/posts")
    public Result<ForumPostVO> createPost(@Valid @RequestBody CreatePostDTO dto) {
        String userId = getCurrentUserIdOrThrow();
        ForumPostVO post = forumService.createPost(dto, userId);
        return Result.success(post);
    }

    @Operation(summary = "Update post", description = "Update an existing post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:update-post", limit = 20, period = 60)
    @PatchMapping("/posts/{id}")
    public Result<ForumPostVO> updatePost(
            @Parameter(description = "Post ID") @PathVariable String id,
            @Valid @RequestBody UpdatePostDTO dto) {
        String userId = getCurrentUserIdOrThrow();
        ForumPostVO post = forumService.updatePost(id, dto, userId);
        return Result.success(post);
    }

    @Operation(summary = "Delete post", description = "Delete a post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:delete-post", limit = 20, period = 60)
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@Parameter(description = "Post ID") @PathVariable String id) {
        String userId = getCurrentUserIdOrThrow();
        forumService.deletePost(id, userId);
        return Result.success();
    }

    @Operation(summary = "Get post thread", description = "Get a post with its comment thread")
    @GetMapping("/posts/{id}/thread")
    public Result<ForumPostThreadVO> getPostThread(@Parameter(description = "Post ID") @PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        ForumPostThreadVO thread = forumService.getPostThread(id, userId);
        return Result.success(thread);
    }

    @Operation(summary = "Record share", description = "Record a share action for a post")
    @RateLimit(key = "forum:share", limit = 20, period = 60)
    @PostMapping("/posts/{id}/share")
    public Result<Void> recordShare(@Parameter(description = "Post ID") @PathVariable String id) {
        forumService.recordShare(id);
        return Result.success();
    }

    @Operation(summary = "Record view", description = "Record a view action for a post")
    @RateLimit(key = "forum:view", limit = 20, period = 60)
    @PostMapping("/posts/{id}/view")
    public Result<Void> recordView(@Parameter(description = "Post ID") @PathVariable String id) {
        forumService.recordView(id);
        return Result.success();
    }

    @Operation(summary = "Create comment", description = "Create a comment on a post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:create-comment", limit = 20, period = 60)
    @PostMapping("/posts/{id}/comments")
    public Result<ForumCommentVO> createComment(
            @Parameter(description = "Post ID") @PathVariable String id,
            @Valid @RequestBody CreateCommentDTO dto) {
        String userId = getCurrentUserIdOrThrow();
        ForumCommentVO comment = forumService.createComment(id, dto, userId);
        return Result.success(comment);
    }

    @Operation(summary = "Update comment", description = "Update an existing comment")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:update-comment", limit = 20, period = 60)
    @PatchMapping("/comments/{id}")
    public Result<ForumCommentVO> updateComment(
            @Parameter(description = "Comment ID") @PathVariable String id,
            @Valid @RequestBody UpdateCommentDTO dto) {
        String userId = getCurrentUserIdOrThrow();
        ForumCommentVO comment = forumService.updateComment(id, dto, userId);
        return Result.success(comment);
    }

    @Operation(summary = "Delete comment", description = "Delete a comment")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:delete-comment", limit = 20, period = 60)
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@Parameter(description = "Comment ID") @PathVariable String id) {
        String userId = getCurrentUserIdOrThrow();
        forumService.deleteComment(id, userId);
        return Result.success();
    }

    @Operation(summary = "Get all communities", description = "Get all forum communities")
    @GetMapping("/communities")
    public Result<List<ForumCommunityVO>> getAllCommunities(
            @Parameter(description = "Filter for featured communities only")
            @RequestParam(required = false, defaultValue = "false") Boolean featured) {
        List<ForumCommunityVO> communities = forumService.findAllCommunities(featured);
        return Result.success(communities);
    }

    @Operation(summary = "Get community", description = "Get a community by slug or ID")
    @GetMapping("/communities/{slugOrId}")
    public Result<ForumCommunityDetailVO> getCommunity(
            @Parameter(description = "Community slug or ID") @PathVariable String slugOrId) {
        ForumCommunityDetailVO community = forumService.findCommunityBySlugOrId(slugOrId);
        return Result.success(community);
    }

    @Operation(summary = "Get community posts", description = "Get posts for a specific community")
    @GetMapping("/communities/{slug}/posts")
    public Result<PageResult<ForumPostVO>> getCommunityPosts(
            @Parameter(description = "Community slug") @PathVariable String slug,
            @Parameter(description = "Sort by (hot, new, top)")
            @RequestParam(required = false, defaultValue = "new") String sortBy,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1")
            @Min(value = 1, message = "page must be at least 1")
            @Max(value = 1000, message = "page cannot exceed 1000")
            Integer page,
            @Parameter(description = "Items per page")
            @RequestParam(required = false, defaultValue = "20")
            @Min(value = 1, message = "pageSize must be at least 1")
            @Max(value = 50, message = "pageSize cannot exceed 50")
            Integer pageSize) {
        String userId = SecurityUtil.getCurrentUserId();
        PageResult<ForumPostVO> result = forumService.findPostsByCommunity(slug, sortBy, userId, page, pageSize);
        return Result.success(result);
    }

    @Operation(summary = "Join community", description = "Join a community")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:join-community", limit = 20, period = 60)
    @PostMapping("/communities/{id}/join")
    public Result<Void> joinCommunity(@Parameter(description = "Community ID") @PathVariable String id) {
        String userId = getCurrentUserIdOrThrow();
        forumService.joinCommunity(id, userId);
        return Result.success();
    }

    @Operation(summary = "Leave community", description = "Leave a community")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:leave-community", limit = 20, period = 60)
    @PostMapping("/communities/{id}/leave")
    public Result<Void> leaveCommunity(@Parameter(description = "Community ID") @PathVariable String id) {
        String userId = getCurrentUserIdOrThrow();
        forumService.leaveCommunity(id, userId);
        return Result.success();
    }

    @Operation(summary = "Get all tags", description = "Get all forum tags")
    @GetMapping("/tags")
    public Result<List<ForumTagVO>> getAllTags() {
        List<ForumTagVO> tags = forumService.findAllTags();
        return Result.success(tags);
    }

    @Operation(summary = "Get quick filters", description = "Get available quick filter options for forum posts")
    @GetMapping("/quick-filters")
    public Result<List<QuickFilterDTO>> getQuickFilters() {
        List<QuickFilterDTO> filters = forumService.getQuickFilters();
        return Result.success(filters);
    }

    private String getCurrentUserIdOrThrow() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
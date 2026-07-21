package com.ulticode.modules.forum.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.forum.validation.ForumPage;
import com.ulticode.modules.forum.validation.ForumPageSize;
import com.ulticode.modules.forum.dto.CreateCommentDTO;
import com.ulticode.modules.forum.dto.CreatePostDTO;
import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.ForumCommunityDetailVO;
import com.ulticode.modules.forum.dto.ForumCommunityVO;
import com.ulticode.modules.forum.dto.ForumPostThreadVO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.ForumTagVO;
import com.ulticode.modules.forum.dto.QuickFilterDTO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;
import com.ulticode.modules.forum.dto.UpdatePostDTO;
import com.ulticode.modules.forum.projection.ForumReadProjection;
import com.ulticode.modules.forum.service.CommunityMembershipService;
import com.ulticode.modules.forum.service.ForumCommentService;
import com.ulticode.modules.forum.service.ForumPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Forum HTTP endpoints. Reads bind to {@link ForumReadProjection}; writes
 * delegate to {@link ForumPostService}, {@link ForumCommentService}, and
 * {@link CommunityMembershipService} — each owning its invariants behind its
 * own transactional / ban-check proxy methods.
 */
@Tag(name = "Forum", description = "Forum management endpoints")
@RestController
@RequestMapping("/forum")
@RequiredArgsConstructor
@Validated
public class ForumController {

    private final ForumReadProjection forumReadProjection;
    private final ForumPostService forumPostService;
    private final ForumCommentService forumCommentService;
    private final CommunityMembershipService communityMembershipService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Get all posts", description = "Get all forum posts with sorting and pagination")
    @GetMapping("/posts")
    public Result<PageResult<ForumPostVO>> getAllPosts(
            @Parameter(description = "Sort by (new, hot, top, controversial, explore)")
            @RequestParam(required = false, defaultValue = "new") String sortBy,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") @ForumPage Integer page,
            @Parameter(description = "Items per page")
            @RequestParam(required = false, defaultValue = "20") @ForumPageSize Integer pageSize) {
        return Result.success(forumReadProjection.findAllPosts(
                currentUserProvider.getCurrentUserId(), sortBy, page, pageSize));
    }

    @Operation(summary = "Get post by ID", description = "Get a specific post by its ID")
    @GetMapping("/posts/{id}")
    public Result<ForumPostVO> getPostById(@Parameter(description = "Post ID") @PathVariable String id) {
        return Result.success(forumReadProjection.findPostById(id, currentUserProvider.getCurrentUserId()));
    }

    @Operation(summary = "Get my posts", description = "Get the current user's posts")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/me/posts")
    public Result<PageResult<ForumPostVO>> getMyPosts(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") @ForumPage Integer page,
            @Parameter(description = "Items per page")
            @RequestParam(required = false, defaultValue = "20") @ForumPageSize Integer pageSize) {
        return Result.success(forumReadProjection.findMyPosts(getCurrentUserIdOrThrow(), page, pageSize));
    }

    @Operation(summary = "Create post", description = "Create a new forum post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:create-post", limit = 20, period = 60)
    @PostMapping("/posts")
    public Result<ForumPostVO> createPost(@Valid @RequestBody CreatePostDTO dto) {
        return Result.success(forumPostService.createPost(dto, getCurrentUserIdOrThrow()));
    }

    @Operation(summary = "Update post", description = "Update an existing post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:update-post", limit = 20, period = 60)
    @PatchMapping("/posts/{id}")
    public Result<ForumPostVO> updatePost(
            @Parameter(description = "Post ID") @PathVariable String id,
            @Valid @RequestBody UpdatePostDTO dto) {
        return Result.success(forumPostService.updatePost(id, dto, getCurrentUserIdOrThrow()));
    }

    @Operation(summary = "Delete post", description = "Delete a post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:delete-post", limit = 20, period = 60)
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@Parameter(description = "Post ID") @PathVariable String id) {
        forumPostService.deletePost(id, getCurrentUserIdOrThrow());
        return Result.success();
    }

    @Operation(summary = "Get post thread", description = "Get a post with its comment thread")
    @GetMapping("/posts/{id}/thread")
    public Result<ForumPostThreadVO> getPostThread(
            @Parameter(description = "Post ID") @PathVariable String id) {
        return Result.success(forumReadProjection.getPostThread(id, currentUserProvider.getCurrentUserId()));
    }

    @Operation(summary = "Record share", description = "Record a share action for a post")
    @RateLimit(key = "forum:share", limit = 20, period = 60)
    @PostMapping("/posts/{id}/share")
    public Result<Void> recordShare(@Parameter(description = "Post ID") @PathVariable String id) {
        forumPostService.recordShare(id);
        return Result.success();
    }

    @Operation(summary = "Record view", description = "Record a view action for a post")
    @RateLimit(key = "forum:view", limit = 20, period = 60)
    @PostMapping("/posts/{id}/view")
    public Result<Void> recordView(@Parameter(description = "Post ID") @PathVariable String id) {
        forumPostService.recordView(id);
        return Result.success();
    }

    @Operation(summary = "Create comment", description = "Create a comment on a post")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:create-comment", limit = 20, period = 60)
    @PostMapping("/posts/{id}/comments")
    public Result<ForumCommentVO> createComment(
            @Parameter(description = "Post ID") @PathVariable String id,
            @Valid @RequestBody CreateCommentDTO dto) {
        return Result.success(forumCommentService.createComment(id, dto, getCurrentUserIdOrThrow()));
    }

    @Operation(summary = "Update comment", description = "Update an existing comment")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:update-comment", limit = 20, period = 60)
    @PatchMapping("/comments/{id}")
    public Result<ForumCommentVO> updateComment(
            @Parameter(description = "Comment ID") @PathVariable String id,
            @Valid @RequestBody UpdateCommentDTO dto) {
        return Result.success(forumCommentService.updateComment(id, dto, getCurrentUserIdOrThrow()));
    }

    @Operation(summary = "Delete comment", description = "Delete a comment")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:delete-comment", limit = 20, period = 60)
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@Parameter(description = "Comment ID") @PathVariable String id) {
        forumCommentService.deleteComment(id, getCurrentUserIdOrThrow());
        return Result.success();
    }

    @Operation(summary = "Get all communities", description = "Get all forum communities")
    @GetMapping("/communities")
    public Result<List<ForumCommunityVO>> getAllCommunities(
            @Parameter(description = "Filter for featured communities only")
            @RequestParam(required = false, defaultValue = "false") Boolean featured) {
        return Result.success(forumReadProjection.findAllCommunities(Boolean.TRUE.equals(featured)));
    }

    @Operation(summary = "Get community", description = "Get a community by slug or ID")
    @GetMapping("/communities/{slugOrId}")
    public Result<ForumCommunityDetailVO> getCommunity(
            @Parameter(description = "Community slug or ID") @PathVariable String slugOrId) {
        return Result.success(forumReadProjection.findCommunityBySlugOrId(slugOrId));
    }

    @Operation(summary = "Get community posts", description = "Get posts for a specific community")
    @GetMapping("/communities/{slug}/posts")
    public Result<PageResult<ForumPostVO>> getCommunityPosts(
            @Parameter(description = "Community slug") @PathVariable String slug,
            @Parameter(description = "Sort by (new, hot, top, controversial, explore)")
            @RequestParam(required = false, defaultValue = "new") String sortBy,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") @ForumPage Integer page,
            @Parameter(description = "Items per page")
            @RequestParam(required = false, defaultValue = "20") @ForumPageSize Integer pageSize) {
        return Result.success(forumReadProjection.findPostsByCommunity(
                slug, sortBy, currentUserProvider.getCurrentUserId(), page, pageSize));
    }

    @Operation(summary = "Join community", description = "Join a community")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:join-community", limit = 20, period = 60)
    @PostMapping("/communities/{id}/join")
    public Result<Void> joinCommunity(@Parameter(description = "Community ID") @PathVariable String id) {
        communityMembershipService.joinCommunity(id, getCurrentUserIdOrThrow());
        return Result.success();
    }

    @Operation(summary = "Leave community", description = "Leave a community")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "forum:leave-community", limit = 20, period = 60)
    @PostMapping("/communities/{id}/leave")
    public Result<Void> leaveCommunity(@Parameter(description = "Community ID") @PathVariable String id) {
        communityMembershipService.leaveCommunity(id, getCurrentUserIdOrThrow());
        return Result.success();
    }

    @Operation(summary = "Get all tags", description = "Get all forum tags")
    @GetMapping("/tags")
    public Result<List<ForumTagVO>> getAllTags() {
        return Result.success(forumReadProjection.findAllTags());
    }

    @Operation(summary = "Get quick filters", description = "Get available quick filter options for forum posts")
    @GetMapping("/quick-filters")
    public Result<List<QuickFilterDTO>> getQuickFilters() {
        return Result.success(forumReadProjection.getQuickFilters());
    }

    private String getCurrentUserIdOrThrow() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}

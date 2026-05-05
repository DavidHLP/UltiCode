package com.ulticode.modules.admin.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.admin.dto.AdminForumPostVO;
import com.ulticode.modules.admin.dto.BulkActionRequest;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.service.AdminForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for forum post management.
 */
@Tag(name = "Admin - Forum", description = "Forum management endpoints for admin panel")
@RestController
@RequestMapping("/admin/forum")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminForumController {

    private final AdminForumService adminForumService;

    @Operation(summary = "Get forum posts", description = "Get paginated list of forum posts with filters")
    @GetMapping("/posts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminForumPostVO>> getPosts(AdminForumPostQueryDTO query) {
        return Result.success(adminForumService.getPosts(query));
    }

    @Operation(summary = "Get post by ID", description = "Get detailed forum post information")
    @GetMapping("/posts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminForumPostVO> getPost(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        return Result.success(adminForumService.getPost(id));
    }

    @Operation(summary = "Pin post", description = "Pin a post to top")
    @RateLimit(key = "admin:forum-pin", limit = 30, period = 60)
    @PostMapping("/posts/{id}/pin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> pinPost(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        adminForumService.pinPost(id);
        return Result.success();
    }

    @Operation(summary = "Unpin post", description = "Unpin a post")
    @RateLimit(key = "admin:forum-unpin", limit = 30, period = 60)
    @PostMapping("/posts/{id}/unpin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> unpinPost(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        adminForumService.unpinPost(id);
        return Result.success();
    }

    @Operation(summary = "Lock post", description = "Lock a post (no comments allowed)")
    @RateLimit(key = "admin:forum-lock", limit = 30, period = 60)
    @PostMapping("/posts/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> lockPost(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        adminForumService.lockPost(id);
        return Result.success();
    }

    @Operation(summary = "Unlock post", description = "Unlock a post")
    @RateLimit(key = "admin:forum-unlock", limit = 30, period = 60)
    @PostMapping("/posts/{id}/unlock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> unlockPost(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        adminForumService.unlockPost(id);
        return Result.success();
    }

    @Operation(summary = "Delete post", description = "Delete a post (soft delete)")
    @RateLimit(key = "admin:forum-delete", limit = 30, period = 60)
    @DeleteMapping("/posts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deletePost(
            @Parameter(description = "Post ID")
            @PathVariable String id) {
        adminForumService.deletePost(id);
        return Result.success();
    }

    @Operation(summary = "Bulk action", description = "Perform action on multiple posts")
    @RateLimit(key = "admin:forum-bulk", limit = 30, period = 60)
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BulkActionResult> bulkAction(
            @Valid @RequestBody BulkActionRequest request) {
        return Result.success(adminForumService.bulkAction(request.getIds(), request.getAction()));
    }

    @Operation(summary = "Get communities", description = "Get list of communities for filtering")
    @GetMapping("/communities")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminForumCommunityVO>> getCommunities(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(adminForumService.getCommunities(page, limit));
    }

    /**
     * Simple VO for community list in admin panel.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class AdminForumCommunityVO {
        private String id;
        private String name;
        private String slug;
        private String description;
        private Integer postCount;
        private Integer memberCount;
    }
}

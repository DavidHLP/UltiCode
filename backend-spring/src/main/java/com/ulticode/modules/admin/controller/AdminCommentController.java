package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.dto.BulkCommentActionRequest;
import com.ulticode.modules.admin.service.AdminCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin controller for comment management.
 */
@Tag(name = "Admin - Comments", description = "Comment management endpoints for admin panel")
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    @Operation(summary = "Get comments", description = "Get paginated list of comments with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminCommentVO>> getComments(AdminCommentQueryDTO query) {
        return Result.success(adminCommentService.getComments(query));
    }

    @Operation(summary = "Get comment by ID and type", description = "Get detailed comment information")
    @GetMapping("/{type}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminCommentVO> getComment(
            @Parameter(description = "Comment type (forum or solution)")
            @PathVariable String type,
            @Parameter(description = "Comment ID")
            @PathVariable String id) {
        return Result.success(adminCommentService.getComment(id, type));
    }

    @Operation(summary = "Flag comment", description = "Flag a comment for review")
    @PatchMapping("/{type}/{id}/flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> flagComment(
            @Parameter(description = "Comment type (forum or solution)")
            @PathVariable String type,
            @Parameter(description = "Comment ID")
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        adminCommentService.flagComment(id, type, reason);
        return Result.success();
    }

    @Operation(summary = "Unflag comment", description = "Remove flag from a comment")
    @PatchMapping("/{type}/{id}/unflag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> unflagComment(
            @Parameter(description = "Comment type (forum or solution)")
            @PathVariable String type,
            @Parameter(description = "Comment ID")
            @PathVariable String id) {
        adminCommentService.unflagComment(id, type);
        return Result.success();
    }

    @Operation(summary = "Delete comment", description = "Delete a comment (soft delete)")
    @DeleteMapping("/{type}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteComment(
            @Parameter(description = "Comment type (forum or solution)")
            @PathVariable String type,
            @Parameter(description = "Comment ID")
            @PathVariable String id) {
        adminCommentService.deleteComment(id, type);
        return Result.success();
    }

    @Operation(summary = "Bulk action", description = "Perform action on multiple comments")
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BulkActionResult> bulkAction(
            @Valid @RequestBody BulkCommentActionRequest request) {
        return Result.success(adminCommentService.bulkCommentAction(request));
    }
}

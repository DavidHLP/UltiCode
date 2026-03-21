package com.ulticode.modules.moderation.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.moderation.dto.*;
import com.ulticode.modules.moderation.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for moderation operations.
 */
@Tag(name = "Moderation", description = "Content moderation API")
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class ModerationController {

    private final ModerationService moderationService;

    // ==================== Queue Operations ====================

    @Operation(summary = "Get moderation queue")
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ModerationQueueVO>> getQueue(QueryModerationQueueDTO query) {
        return Result.success(moderationService.getQueueItems(query));
    }

    @Operation(summary = "Get moderation statistics")
    @GetMapping("/queue/stats")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationStatsVO> getStats() {
        return Result.success(moderationService.getStats());
    }

    @Operation(summary = "Get queue item details")
    @GetMapping("/queue/{id}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> getQueueItem(@PathVariable String id) {
        return Result.success(moderationService.getQueueItem(id));
    }

    @Operation(summary = "Claim a queue item")
    @PostMapping("/queue/{id}/claim")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> claim(@PathVariable String id) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.claimItem(id, moderatorId));
    }

    @Operation(summary = "Assign a queue item to a moderator")
    @PostMapping("/queue/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> assign(@PathVariable String id, @Valid @RequestBody AssignDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.assignItem(id, moderatorId, dto.getAssignedTo()));
    }

    @Operation(summary = "Unassign a queue item")
    @PatchMapping("/queue/{id}/unassign")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> unassign(@PathVariable String id) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.unassignItem(id, moderatorId));
    }

    @Operation(summary = "Perform moderation action")
    @PostMapping("/queue/{id}/action")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> performAction(
            @PathVariable String id,
            @Valid @RequestBody PerformModerationActionDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.performAction(id, dto, moderatorId));
    }

    @Operation(summary = "Find queue item by entity")
    @GetMapping("/queue/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> findByEntity(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        return Result.success(moderationService.findByEntity(entityType, entityId));
    }

    @Operation(summary = "Batch moderation action")
    @PostMapping("/queue/batch-action")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BatchActionResultVO> batchAction(@Valid @RequestBody BatchModerationActionDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.batchAction(dto, moderatorId));
    }

    // ==================== Report Operations ====================

    @Operation(summary = "Create a report")
    @PostMapping("/reports")
    public Result<Void> createReport(@Valid @RequestBody CreateReportDTO dto) {
        String reporterId = SecurityUtil.getCurrentUserId();
        moderationService.createReport(dto, reporterId);
        return Result.success();
    }

    @Operation(summary = "Get reports for an entity")
    @GetMapping("/reports/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<List<ReportVO>> getReportsForEntity(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        return Result.success(moderationService.getReportsForEntity(entityType, entityId));
    }

    @Operation(summary = "Get paginated reports")
    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ReportVO>> getReports(QueryReportsDTO query) {
        return Result.success(moderationService.getReports(query));
    }

    // ==================== Appeal Operations ====================

    @Operation(summary = "Create an appeal")
    @PostMapping("/appeals")
    public Result<AppealVO> createAppeal(@Valid @RequestBody CreateAppealDTO dto) {
        String appellantId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.createAppeal(dto, appellantId));
    }

    @Operation(summary = "Get paginated appeals")
    @GetMapping("/appeals")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AppealVO>> getAppeals(QueryAppealsDTO query) {
        return Result.success(moderationService.getAppeals(query));
    }

    @Operation(summary = "Get appeal details")
    @GetMapping("/appeals/{id}")
    public Result<AppealVO> getAppeal(@PathVariable String id) {
        return Result.success(moderationService.getAppeal(id));
    }

    @Operation(summary = "Review an appeal")
    @PostMapping("/appeals/{id}/review")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<AppealVO> reviewAppeal(
            @PathVariable String id,
            @Valid @RequestBody ReviewAppealDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.reviewAppeal(id, dto, moderatorId));
    }
}

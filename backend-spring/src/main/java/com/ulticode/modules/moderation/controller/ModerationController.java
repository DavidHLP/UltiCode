package com.ulticode.modules.moderation.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.moderation.dto.*;
import com.ulticode.modules.moderation.projection.ModerationProjection;
import com.ulticode.modules.moderation.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for moderation operations.
 *
 * <p>Read paths (queue / report / appeal list, detail, stats) depend on
 * {@link ModerationProjection} directly; write paths and the
 * authorisation-guarded appeal detail depend on {@link ModerationService}.
 */
@Tag(name = "Moderation", description = "Content moderation API")
@RestController
@RequestMapping("/moderation")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class ModerationController {

    private final ModerationService moderationService;
    private final ModerationProjection moderationProjection;

    // ==================== Queue Operations ====================

    @Operation(summary = "Get moderation queue")
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ModerationQueueVO>> getQueue(QueryModerationQueueDTO query) {
        return Result.success(moderationProjection.listQueueItems(query));
    }

    @Operation(summary = "Get moderation statistics")
    @GetMapping("/queue/stats")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationStatsVO> getStats() {
        return Result.success(moderationProjection.stats());
    }

    @Operation(summary = "Get moderation enums")
    @GetMapping("/enums")
    public Result<Map<String, List<String>>> getEnums() {
        Map<String, List<String>> enums = new HashMap<>();
        enums.put("actionTypes", List.of("DELETED", "HIDDEN", "RESTORED", "DISMISSED", "RESOLVED",
                "WARNED", "TEMP_BANNED", "PERM_BANNED"));
        enums.put("statuses", List.of("PENDING", "UNDER_REVIEW", "RESOLVED", "DISMISSED", "APPEAL_PENDING"));
        enums.put("reportCategories", List.of("SPAM", "HARASSMENT", "HATE_SPEECH", "VIOLENCE",
                "SEXUAL_CONTENT", "MISINFORMATION", "WRONG_ANSWER", "COPYRIGHT", "OTHER"));
        enums.put("appealStatuses", List.of("PENDING", "UNDER_REVIEW", "APPROVED", "REJECTED"));
        return Result.success(enums);
    }

    @Operation(summary = "Get queue item details")
    @GetMapping("/queue/{id}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> getQueueItem(@PathVariable String id) {
        return Result.success(moderationProjection.queueItemById(id));
    }

    @Operation(summary = "Claim a queue item")
    @RateLimit(key = "admin:moderation-claim", limit = 30, period = 60)
    @PostMapping("/queue/{id}/claim")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> claim(@PathVariable String id) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.claimItem(id, moderatorId));
    }

    @Operation(summary = "Assign a queue item to a moderator")
    @RateLimit(key = "admin:moderation-assign", limit = 30, period = 60)
    @PostMapping("/queue/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> assign(@PathVariable String id, @Valid @RequestBody AssignDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.assignItem(id, moderatorId, dto.getAssignedTo()));
    }

    @Operation(summary = "Unassign a queue item")
    @RateLimit(key = "admin:moderation-unassign", limit = 30, period = 60)
    @PatchMapping("/queue/{id}/unassign")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ModerationQueueVO> unassign(@PathVariable String id) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.unassignItem(id, moderatorId));
    }

    @Operation(summary = "Perform moderation action")
    @RateLimit(key = "admin:moderation-action", limit = 30, period = 60)
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
        return Result.success(moderationProjection.queueItemByEntity(entityType, entityId));
    }

    @Operation(summary = "Batch moderation action")
    @RateLimit(key = "admin:moderation-batch", limit = 30, period = 60)
    @PostMapping("/queue/batch-action")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BatchActionResultVO> batchAction(@Valid @RequestBody BatchModerationActionDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.batchAction(dto, moderatorId));
    }

    // ==================== Report Operations ====================

    @Operation(summary = "Create a report")
    @RateLimit(key = "moderation:create-report", limit = 20, period = 60)
    @PostMapping("/reports")
    @PreAuthorize("isAuthenticated()")
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
        return Result.success(moderationProjection.reportsForEntity(entityType, entityId));
    }

    @Operation(summary = "Get paginated reports")
    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ReportVO>> getReports(QueryReportsDTO query) {
        return Result.success(moderationProjection.listReports(query));
    }

    @Operation(summary = "Get report details")
    @GetMapping("/reports/{id}")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ReportVO> getReport(@PathVariable String id) {
        return Result.success(moderationProjection.reportById(id));
    }

    // ==================== Appeal Operations ====================

    @Operation(summary = "Create an appeal")
    @RateLimit(key = "moderation:create-appeal", limit = 20, period = 60)
    @PostMapping("/appeals")
    @PreAuthorize("isAuthenticated()")
    public Result<AppealVO> createAppeal(@Valid @RequestBody CreateAppealDTO dto) {
        String appellantId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.createAppeal(dto, appellantId));
    }

    @Operation(summary = "Get paginated appeals")
    @GetMapping("/appeals")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AppealVO>> getAppeals(QueryAppealsDTO query) {
        return Result.success(moderationProjection.listAppeals(query));
    }

    @Operation(summary = "Get appeal details", description = "Returns a single appeal. "
            + "Access is restricted: only the appellant, or a MOD/ADMIN/SUPER_ADMIN, may read. "
            + "Other authenticated users receive 403 Forbidden. Rate-limited to 30 req/min/key.")
    @RateLimit(key = "moderation:appeal-detail", limit = 30, period = 60)
    @GetMapping("/appeals/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<AppealVO> getAppeal(@PathVariable String id) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.getAppeal(id, currentUserId));
    }

    @Operation(summary = "Get current user's appeals")
    @GetMapping("/appeals/my")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AppealVO>> getMyAppeals() {
        String appellantId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationProjection.myAppeals(appellantId));
    }

    @Operation(summary = "Get appeal statistics")
    @GetMapping("/appeals/stats")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<AppealStatsVO> getAppealStats() {
        return Result.success(moderationProjection.appealStats());
    }

    @Operation(summary = "Review an appeal",
            description = "Approve or reject an appeal. Body uses `decision` field with values "
                    + "`APPROVED` or `REJECTED` (NOT `status`). `response` is an optional moderator note.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = ReviewAppealDTO.class)))
    @RateLimit(key = "admin:moderation-review", limit = 30, period = 60)
    @PostMapping("/appeals/{id}/review")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    public Result<AppealVO> reviewAppeal(
            @PathVariable String id,
            @Valid @RequestBody ReviewAppealDTO dto) {
        String moderatorId = SecurityUtil.getCurrentUserId();
        return Result.success(moderationService.reviewAppeal(id, dto, moderatorId));
    }
}

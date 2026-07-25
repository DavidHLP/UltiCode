package com.ulticode.modules.admin.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.projection.AdminSubmissionProjection;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for submission management.
 *
 * <p>Depends on {@link AdminSubmissionProjection} for reads and
 * {@link AdminSubmissionService} for writes (ADR-0011 Stage 2 split).
 */
@Tag(name = "Admin - Submissions", description = "Submission management endpoints for admin panel")
@RestController
@RequestMapping("/admin/submissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminSubmissionController {

    private final AdminSubmissionProjection adminSubmissionProjection;
    private final AdminSubmissionService adminSubmissionService;

    @Operation(summary = "Get submissions", description = "Get paginated list of submissions with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminSubmissionVO>> getSubmissions(AdminSubmissionQueryDTO query) {
        return Result.success(adminSubmissionProjection.getSubmissions(query));
    }

    @Operation(summary = "Get submission by ID", description = "Get detailed submission information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminSubmissionVO> getSubmission(@PathVariable String id) {
        return Result.success(adminSubmissionProjection.getSubmission(id));
    }

    @Operation(summary = "Get submission statistics", description = "Get statistics for admin dashboard")
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<SubmissionStatistics> getStatistics() {
        return Result.success(adminSubmissionProjection.getStatistics());
    }

    @Operation(summary = "Get status options", description = "Get available status options for filtering")
    @GetMapping("/statuses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<StatusOption>> getStatuses() {
        return Result.success(adminSubmissionProjection.getStatuses());
    }

    @Operation(summary = "Get languages", description = "Get available programming languages for filtering")
    @GetMapping("/languages")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<LanguageOption>> getLanguages() {
        return Result.success(adminSubmissionProjection.getLanguages());
    }

    @Operation(summary = "Rejudge submission", description = "Rejudge a single submission")
    @RateLimit(key = "admin:submission-rejudge", limit = 5, period = 60)
    @PostMapping("/{id}/rejudge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<RejudgeResult> rejudge(
            @PathVariable String id,
            @Valid @RequestBody RejudgeRequest request) {
        return Result.success(adminSubmissionService.rejudge(id, request.getNotifyUser()));
    }

    @Operation(summary = "Batch rejudge", description = "Rejudge multiple submissions")
    @RateLimit(key = "admin:submission-batch-rejudge", limit = 5, period = 60)
    @PostMapping("/batch-rejudge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BatchRejudgeResponse> batchRejudge(@Valid @RequestBody BatchRejudgeRequest request) {
        return Result.success(adminSubmissionService.batchRejudge(
            request.getSubmissionIds(), request.getNotifyUsers()));
    }
}

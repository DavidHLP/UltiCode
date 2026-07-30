package com.ulticode.modules.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.List;
import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.admin.service.ProblemCutoverService;
import com.ulticode.modules.admin.service.ProblemExportService;
import com.ulticode.modules.admin.service.ProblemImportService;
import com.ulticode.modules.admin.service.impl.ExportPayload;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.modules.submission.entity.Submission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for problem management
 */
@Tag(name = "Admin - Problems", description = "题目管理接口")
@RestController
@RequestMapping("/admin/problems")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminProblemController {

    private final ProblemService problemService;
    private final ProblemCutoverService problemCutoverService;
    private final ProblemProjection problemProjection;
    private final AdminProblemService adminProblemService;
    private final ProblemExportService problemExportService;
    private final ProblemImportService problemImportService;

    @Operation(summary = "Get problems list", description = "Get paginated list of problems with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProblemVO>> getProblems(ProblemQueryDTO query) {
        return Result.success(problemProjection.listProblems(query));
    }

    @Operation(summary = "Export problems", description = "Export problems as JSON or CSV file")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void exportProblems(ProblemQueryDTO query,
                               @RequestParam(defaultValue = "json") String format,
                               HttpServletResponse response) throws IOException {
        // All shaping (format validation, size cap, CSV header/escape, date
        // stamp via the Clock seam) lives in ProblemExportService; the
        // controller only sets headers and dispatches the payload to the
        // response. Format selection is hidden inside ExportPayload.writeTo,
        // so this method has no per-format branch. Format errors throw
        // BusinessException(BAD_REQUEST) and are shaped by the global
        // exception handler.
        ExportPayload payload = problemExportService.export(query, format);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(payload.contentType());
        response.setHeader("Content-Disposition", "attachment; filename=" + payload.filename());
        payload.writeTo(response);
    }

    @Operation(summary = "Get problem by ID", description = "Get detailed problem information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> getProblemById(@PathVariable Long id) {
        return Result.success(problemService.getProblemById(id));
    }

    @Operation(summary = "Create problem", description = "Create a new problem")
    @RateLimit(key = "admin:problem-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> createProblem(@Valid @RequestBody CreateProblemDTO createDTO) {
        return Result.success(problemCutoverService.createProblem(createDTO));
    }

    @Operation(summary = "Update problem", description = "Update an existing problem")
    @RateLimit(key = "admin:problem-update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> updateProblem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProblemDTO updateDTO) {
        return Result.success(problemCutoverService.updateProblem(id, updateDTO));
    }

    @Operation(summary = "Delete problem", description = "Delete a problem (soft delete)")
    @RateLimit(key = "admin:problem-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteProblem(@PathVariable Long id) {
        problemCutoverService.deleteProblem(id);
        return Result.success();
    }

    @Operation(summary = "Publish problem", description = "Publish a problem")
    @RateLimit(key = "admin:problem-publish", limit = 30, period = 60)
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> publishProblem(@PathVariable Long id) {
        return Result.success(problemCutoverService.publishProblem(id));
    }

    @Operation(summary = "Unpublish problem", description = "Unpublish a problem")
    @RateLimit(key = "admin:problem-unpublish", limit = 30, period = 60)
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> unpublishProblem(@PathVariable Long id) {
        return Result.success(problemCutoverService.unpublishProblem(id));
    }

    @Operation(summary = "Bulk problem action", description = "Perform bulk action on multiple problems (publish, unpublish, delete, restore, edit)")
    @RateLimit(key = "admin:problem-bulk", limit = 10, period = 60)
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<BulkProblemResultDTO>> bulkAction(@Valid @RequestBody BulkProblemRequestDTO request) {
        return Result.success(adminProblemService.bulkAction(request));
    }

    @Operation(summary = "Flag problem", description = "Flag a problem for review")
    @RateLimit(key = "admin:problem-flag", limit = 30, period = 60)
    @PostMapping("/{id}/flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> flagProblem(@PathVariable Long id, @RequestBody @Valid FlagProblemRequestDTO request) {
        return Result.success(adminProblemService.flagProblem(id, request.getReason()));
    }

    @Operation(summary = "Moderate problem", description = "Moderate a flagged problem")
    @RateLimit(key = "admin:problem-moderate", limit = 30, period = 60)
    @PostMapping("/{id}/moderate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> moderateProblem(@PathVariable Long id, @RequestBody @Valid ModerateProblemRequestDTO request) {
        return Result.success(adminProblemService.moderateProblem(id, request.getStatus(), request.getNotes()));
    }

    @Operation(summary = "Get flagged problems", description = "Get paginated list of flagged problems")
    @RateLimit(key = "admin:problem-flagged", limit = 60, period = 60)
    @GetMapping("/flagged")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProblemVO>> getFlaggedProblems(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(adminProblemService.getFlaggedProblems(status, page, limit));
    }

    @Operation(summary = "Batch moderate flagged problems", description = "Batch moderate multiple flagged problems")
    @RateLimit(key = "admin:problem-batch-moderate", limit = 10, period = 60)
    @PostMapping("/flagged/batch-moderate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<BulkProblemResultDTO>> batchModerateProblems(@RequestBody @Valid BatchModerateRequestDTO request) {
        return Result.success(adminProblemService.batchModerateProblems(request));
    }

    @Operation(summary = "Get problem submissions", description = "Get submissions for a specific problem")
    @RateLimit(key = "admin:problem-submissions", limit = 60, period = 60)
    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<Submission>> getProblemSubmissions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(adminProblemService.getProblemSubmissions(id, page, limit));
    }

    @Operation(summary = "Import problems", description = "Bulk import problems from JSON")
    @RateLimit(key = "admin:problem-import", limit = 5, period = 60)
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ImportProblemsResponseDTO> importProblems(@RequestBody @Valid ImportProblemsRequestDTO request) {
        return Result.success(problemImportService.importProblems(request));
    }

    // ========== Tab-specific Endpoints ==========

    @Operation(summary = "Get problem header data", description = "Get header data for problem header tab")
    @GetMapping("/{id}/header")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<HeaderDataVO> getProblemHeader(@PathVariable Long id) {
        return Result.success(adminProblemService.getHeaderData(id));
    }

    @Operation(summary = "Get problem description data", description = "Get description data with details, examples, constraints, and tags")
    @GetMapping("/{id}/description")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<DescriptionDataVO> getProblemDescription(@PathVariable Long id) {
        return Result.success(adminProblemService.getDescriptionData(id));
    }

    @Operation(summary = "Get problem code data", description = "Get code data with language starter codes")
    @GetMapping("/{id}/code")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<CodeDataVO> getProblemCode(@PathVariable Long id) {
        return Result.success(adminProblemService.getCodeData(id));
    }

    @Operation(summary = "Get problem cases data", description = "Get cases data with examples, constraints, and hints")
    @GetMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<CasesDataVO> getProblemCases(@PathVariable Long id) {
        return Result.success(adminProblemService.getCasesData(id));
    }

    @Operation(summary = "Get problem audit history", description = "Get audit history for a problem")
    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AuditLogVO>> getProblemAuditHistory(
            @Parameter(description = "Problem ID")
            @PathVariable Long id) {
        return Result.success(adminProblemService.getProblemAuditHistory(id));
    }
}

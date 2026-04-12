package com.ulticode.modules.admin.controller;

import jakarta.validation.Valid;
import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
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
    private final AdminProblemService adminProblemService;

    @Operation(summary = "Get problems list", description = "Get paginated list of problems with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProblemVO>> getProblems(ProblemQueryDTO query) {
        return Result.success(problemService.listProblems(query));
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
        return Result.success(problemService.createProblem(createDTO));
    }

    @Operation(summary = "Update problem", description = "Update an existing problem")
    @RateLimit(key = "admin:problem-update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> updateProblem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProblemDTO updateDTO) {
        return Result.success(problemService.updateProblem(id, updateDTO));
    }

    @Operation(summary = "Delete problem", description = "Delete a problem (soft delete)")
    @RateLimit(key = "admin:problem-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return Result.success();
    }

    @Operation(summary = "Publish problem", description = "Publish a problem")
    @RateLimit(key = "admin:problem-publish", limit = 30, period = 60)
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> publishProblem(@PathVariable Long id) {
        return Result.success(problemService.publishProblem(id));
    }

    @Operation(summary = "Unpublish problem", description = "Unpublish a problem")
    @RateLimit(key = "admin:problem-unpublish", limit = 30, period = 60)
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> unpublishProblem(@PathVariable Long id) {
        return Result.success(problemService.unpublishProblem(id));
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
}

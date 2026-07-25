package com.ulticode.modules.admin.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.modules.admin.dto.BulkSolutionActionDto;
import com.ulticode.modules.admin.dto.FlagSolutionDto;
import com.ulticode.modules.admin.projection.AdminSolutionProjection;
import com.ulticode.modules.admin.service.AdminSolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin controller for solution management.
 */
@Tag(name = "Admin - Solutions", description = "题解管理接口")
@RestController
@RequestMapping("/admin/solutions")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminSolutionController {

    private final AdminSolutionService adminSolutionService;
    private final AdminSolutionProjection adminSolutionProjection;

    @Operation(summary = "Get solutions", description = "Get paginated list of solutions with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminSolutionListItemVO>> getSolutions(AdminSolutionQueryDTO query) {
        return Result.success(adminSolutionProjection.getSolutions(query));
    }

    @Operation(summary = "Get flagged solutions",
            description = "Get paginated list of currently-active (non-deleted) flagged solutions")
    @GetMapping("/flagged")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminSolutionListItemVO>> getFlaggedSolutions(AdminSolutionQueryDTO query) {
        return Result.success(adminSolutionProjection.getFlaggedSolutions(query));
    }

    @Operation(summary = "Get solution by ID", description = "Get detailed solution information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminSolutionVO> getSolution(@PathVariable String id) {
        return Result.success(adminSolutionProjection.getSolution(id));
    }

    @Operation(summary = "Flag solution", description = "Flag a solution for review; requires a non-blank reason")
    @RateLimit(key = "admin:solution-flag", limit = 30, period = 60)
    @PostMapping("/{id}/flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminSolutionVO> flagSolution(
            @PathVariable String id,
            @Valid @RequestBody FlagSolutionDto dto) {
        return Result.success(adminSolutionService.flagSolution(id, dto.getReason()));
    }

    @Operation(summary = "Unflag solution", description = "Remove flag from a solution (idempotent)")
    @RateLimit(key = "admin:solution-unflag", limit = 30, period = 60)
    @PostMapping("/{id}/unflag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminSolutionVO> unflagSolution(@PathVariable String id) {
        return Result.success(adminSolutionService.unflagSolution(id));
    }

    @Operation(summary = "Delete solution (soft)",
            description = "Soft-delete a solution by setting is_deleted=1. The row remains in the database "
                    + "and can be inspected via GET /admin/solutions?isDeleted=true. Hard delete is not "
                    + "exposed in this version.")
    @RateLimit(key = "admin:solution-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteSolution(@PathVariable String id) {
        adminSolutionService.deleteSolution(id);
        return Result.success();
    }

    @Operation(summary = "Bulk action",
            description = "Perform a bulk action on up to 100 solutions. Action must be one of: "
                    + "publish, unpublish, delete, unflag. To flag solutions in bulk, use "
                    + "POST /admin/solutions/{id}/flag individually with a per-solution reason.")
    @RateLimit(key = "admin:solution-bulk", limit = 30, period = 60)
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminSolutionService.BulkActionResult>> bulkAction(
            @Valid @RequestBody BulkSolutionActionDto dto) {
        return Result.success(adminSolutionService.bulkAction(dto.getIds(), dto.getAction()));
    }
}

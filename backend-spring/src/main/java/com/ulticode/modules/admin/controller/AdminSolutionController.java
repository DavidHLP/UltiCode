package com.ulticode.modules.admin.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.modules.admin.service.AdminSolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Get solutions", description = "Get paginated list of solutions with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminSolutionVO>> getSolutions(AdminSolutionQueryDTO query) {
        return Result.success(adminSolutionService.getSolutions(query));
    }

    @Operation(summary = "Get flagged solutions", description = "Get paginated list of flagged solutions")
    @GetMapping("/flagged")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminSolutionVO>> getFlaggedSolutions(AdminSolutionQueryDTO query) {
        return Result.success(adminSolutionService.getFlaggedSolutions(query));
    }

    @Operation(summary = "Get solution by ID", description = "Get detailed solution information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminSolutionVO> getSolution(@PathVariable String id) {
        return Result.success(adminSolutionService.getSolution(id));
    }

    @Operation(summary = "Flag solution", description = "Flag a solution for review")
    @RateLimit(key = "admin:solution-flag", limit = 30, period = 60)
    @PostMapping("/{id}/flag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminSolutionVO> flagSolution(
            @PathVariable String id,
            @Valid @RequestBody FlagSolutionDto dto,
            @RequestHeader(value = "X-User-Id", required = false) String adminId) {
        return Result.success(adminSolutionService.flagSolution(id, dto.getReason(), adminId));
    }

    @Operation(summary = "Unflag solution", description = "Remove flag from a solution")
    @RateLimit(key = "admin:solution-unflag", limit = 30, period = 60)
    @PostMapping("/{id}/unflag")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminSolutionVO> unflagSolution(@PathVariable String id) {
        return Result.success(adminSolutionService.unflagSolution(id));
    }

    @Operation(summary = "Delete solution", description = "Permanently delete a solution")
    @RateLimit(key = "admin:solution-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteSolution(@PathVariable String id) {
        adminSolutionService.deleteSolution(id);
        return Result.success();
    }

    @Operation(summary = "Bulk action", description = "Perform bulk action on multiple solutions")
    @RateLimit(key = "admin:solution-bulk", limit = 30, period = 60)
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<AdminSolutionService.BulkActionResult>> bulkAction(
            @Valid @RequestBody BulkSolutionActionDto dto) {
        return Result.success(adminSolutionService.bulkAction(dto.getIds(), dto.getAction()));
    }

    /**
     * DTO for flagging a solution.
     */
    @Data
    public static class FlagSolutionDto {
        private String reason;
    }

    /**
     * DTO for bulk action on solutions.
     */
    @Data
    public static class BulkSolutionActionDto {
        private List<String> ids;
        private String action; // publish, unpublish, delete, unflag
    }
}

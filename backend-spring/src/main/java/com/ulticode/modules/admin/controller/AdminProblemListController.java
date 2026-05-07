package com.ulticode.modules.admin.controller;

import jakarta.validation.Valid;
import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.service.AdminProblemListService;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for problem list management.
 */
@Tag(name = "Admin - ProblemLists", description = "题单管理接口")
@RestController
@RequestMapping("/admin/problem-lists")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminProblemListController {

    private final AdminProblemListService adminProblemListService;

    @Operation(summary = "Get problem lists", description = "Get paginated list of problem lists with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProblemListSummaryVO>> getProblemLists(AdminProblemListQueryDTO query) {
        return Result.success(adminProblemListService.getProblemLists(query));
    }

    @Operation(summary = "Get problem list by ID", description = "Get detailed problem list information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListDetailVO> getProblemListById(@PathVariable String id) {
        return Result.success(adminProblemListService.getProblemList(id));
    }

    @Operation(summary = "Create problem list", description = "Create a new problem list")
    @RateLimit(key = "admin:problem-list-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryVO> createProblemList(
            @Valid @RequestBody CreateProblemListDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Result.success(adminProblemListService.createProblemList(dto, userId));
    }

    @Operation(summary = "Update problem list", description = "Update an existing problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryVO> updateProblemList(
            @PathVariable String id,
            @Valid @RequestBody UpdateProblemListDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Result.success(adminProblemListService.updateProblemList(id, dto, userId));
    }

    @Operation(summary = "Delete problem list", description = "Delete a problem list")
    @RateLimit(key = "admin:problem-list-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteProblemList(@PathVariable String id) {
        adminProblemListService.deleteProblemList(id);
        return Result.success();
    }

    @Operation(summary = "Update problem list problems", description = "Replace all problems in a problem list")
    @RateLimit(key = "admin:problem-list-update-problems", limit = 30, period = 60)
    @PostMapping("/{id}/problems")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'MANAGE_PROBLEMS')")
    public Result<Void> updateListProblems(
            @PathVariable String id,
            @Valid @RequestBody UpdateProblemListProblemsDTO dto) {
        adminProblemListService.updateListProblems(id, dto);
        return Result.success();
    }
}

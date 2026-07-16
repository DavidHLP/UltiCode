package com.ulticode.modules.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.service.AdminProblemListService;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
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
            Principal principal) {
        String userId = principal.getName();
        return Result.success(adminProblemListService.createProblemList(dto, userId));
    }

    @Operation(summary = "Update problem list", description = "Update an existing problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryVO> updateProblemList(
            @PathVariable String id,
            @Valid @RequestBody UpdateProblemListDTO dto,
            Principal principal) {
        String userId = principal.getName();
        return Result.success(adminProblemListService.updateProblemList(id, dto, userId));
    }

    @Operation(summary = "Delete problem list", description = "Delete a problem list")
    @RateLimit(key = "admin:problem-list-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteProblemList(@PathVariable String id, Principal principal) {
        adminProblemListService.deleteProblemList(id, principal.getName());
        return Result.success();
    }

    @Operation(summary = "Update problem list problems", description = "Replace all problems in a problem list")
    @RateLimit(key = "admin:problem-list-update-problems", limit = 30, period = 60)
    @PostMapping("/{id}/problems")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'MANAGE_PROBLEMS')")
    public Result<Void> updateListProblems(
            @PathVariable String id,
            @Valid @RequestBody UpdateProblemListProblemsDTO dto,
            Principal principal) {
        adminProblemListService.updateListProblems(id, dto, principal.getName());
        return Result.success();
    }

    @Operation(summary = "Update problem list basic info", description = "Update name and description of a problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}/basic-info")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryVO> updateBasicInfo(
            @PathVariable String id,
            @Valid @RequestBody UpdateBasicInfoDTO dto,
            Principal principal) {
        String userId = principal.getName();
        return Result.success(adminProblemListService.updateBasicInfo(id, userId, dto));
    }

    @Operation(summary = "Update problem list visibility", description = "Update public and featured status of a problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryVO> updateVisibility(
            @PathVariable String id,
            @Valid @RequestBody UpdateVisibilityDTO dto,
            Principal principal) {
        String userId = principal.getName();
        return Result.success(adminProblemListService.updateVisibility(id, userId, dto));
    }

    @Operation(summary = "Update problem list banner", description = "Update banner settings of a problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}/banner")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryVO> updateBanner(
            @PathVariable String id,
            @Valid @RequestBody UpdateBannerDTO dto,
            Principal principal) {
        String userId = principal.getName();
        return Result.success(adminProblemListService.updateBanner(id, userId, dto));
    }
}

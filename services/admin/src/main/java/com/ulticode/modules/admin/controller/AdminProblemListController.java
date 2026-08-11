package com.ulticode.modules.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.dto.CreateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateBannerRequest;
import com.ulticode.modules.admin.dto.UpdateBasicInfoRequest;
import com.ulticode.modules.admin.dto.UpdateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateProblemsRequest;
import com.ulticode.modules.admin.dto.UpdateVisibilityRequest;
import com.ulticode.modules.admin.service.AdminProblemListService;
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
    public Result<PageResult<ProblemListSummaryDTO>> getProblemLists(AdminProblemListQueryDTO query) {
        return Result.success(adminProblemListService.getProblemLists(query));
    }

    @Operation(summary = "Get problem list by ID", description = "Get detailed problem list information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListDetailDTO> getProblemListById(@PathVariable String id) {
        return Result.success(adminProblemListService.getProblemList(id));
    }

    @Operation(summary = "Create problem list", description = "Create a new problem list")
    @RateLimit(key = "admin:problem-list-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryDTO> createProblemList(
            @Valid @RequestBody CreateProblemListRequest dto,
            Principal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String userId = principal.getName();
        ProblemListSummaryDTO result = hasKey(idempotencyKey)
                ? adminProblemListService.createProblemList(dto, userId, idempotencyKey)
                : adminProblemListService.createProblemList(dto, userId);
        return Result.success(result);
    }

    @Operation(summary = "Update problem list", description = "Update an existing problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryDTO> updateProblemList(
            @PathVariable String id,
            @Valid @RequestBody UpdateProblemListRequest dto,
            Principal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String userId = principal.getName();
        ProblemListSummaryDTO result = hasKey(idempotencyKey)
                ? adminProblemListService.updateProblemList(id, dto, userId, idempotencyKey)
                : adminProblemListService.updateProblemList(id, dto, userId);
        return Result.success(result);
    }

    @Operation(summary = "Delete problem list", description = "Delete a problem list")
    @RateLimit(key = "admin:problem-list-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteProblemList(
            @PathVariable String id,
            Principal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        if (hasKey(idempotencyKey)) {
            adminProblemListService.deleteProblemList(id, principal.getName(), idempotencyKey);
        } else {
            adminProblemListService.deleteProblemList(id, principal.getName());
        }
        return Result.success();
    }

    @Operation(summary = "Update problem list problems", description = "Replace all problems in a problem list")
    @RateLimit(key = "admin:problem-list-update-problems", limit = 30, period = 60)
    @PostMapping("/{id}/problems")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> updateListProblems(
            @PathVariable String id,
            @Valid @RequestBody UpdateProblemsRequest dto,
            Principal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        if (hasKey(idempotencyKey)) {
            adminProblemListService.updateListProblems(id, dto, principal.getName(), idempotencyKey);
        } else {
            adminProblemListService.updateListProblems(id, dto, principal.getName());
        }
        return Result.success();
    }

    @Operation(summary = "Update problem list basic info", description = "Update name and description of a problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}/basic-info")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryDTO> updateBasicInfo(
            @PathVariable String id,
            @Valid @RequestBody UpdateBasicInfoRequest dto,
            Principal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String userId = principal.getName();
        ProblemListSummaryDTO result = hasKey(idempotencyKey)
                ? adminProblemListService.updateBasicInfo(id, userId, dto, idempotencyKey)
                : adminProblemListService.updateBasicInfo(id, userId, dto);
        return Result.success(result);
    }

    @Operation(summary = "Update problem list visibility", description = "Update public and featured status of a problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryDTO> updateVisibility(
            @PathVariable String id,
            @Valid @RequestBody UpdateVisibilityRequest dto,
            Principal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String userId = principal.getName();
        ProblemListSummaryDTO result = hasKey(idempotencyKey)
                ? adminProblemListService.updateVisibility(id, userId, dto, idempotencyKey)
                : adminProblemListService.updateVisibility(id, userId, dto);
        return Result.success(result);
    }

    @Operation(summary = "Update problem list banner", description = "Update banner settings of a problem list")
    @RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
    @PatchMapping("/{id}/banner")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemListSummaryDTO> updateBanner(
            @PathVariable String id,
            @Valid @RequestBody UpdateBannerRequest dto,
            Principal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        String userId = principal.getName();
        ProblemListSummaryDTO result = hasKey(idempotencyKey)
                ? adminProblemListService.updateBanner(id, userId, dto, idempotencyKey)
                : adminProblemListService.updateBanner(id, userId, dto);
        return Result.success(result);
    }

    private static boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }
}
